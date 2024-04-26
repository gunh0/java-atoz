// EXP04-J. Do not pass arguments to certain Java Collections Framework methods that are a different type than the collection parameter type
// https://wiki.sei.cmu.edu/confluence/display/java/EXP04-J.+Do+not+pass+arguments+to+certain+Java+Collections+Framework+methods+that+are+a+different+type+than+the+collection+parameter+type
//
// Collection.contains, remove, Map.get, containsKey and friends take an
// Object, not an E, for historical compatibility. The compiler therefore
// accepts a lookup with the wrong type (a Short in a Set<Integer>, an int
// id in a Map<Long, ...>, a String where a domain type was meant) and the
// call simply returns false/null. A revoked token that is "not found"
// because of a type mismatch is still accepted. Make the argument the
// collection's element type, convert explicitly, and prefer typed helper
// methods so the compiler can catch the mistake.
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EXP04_CollectionArgumentTypes {
    public static void main(String[] args) {
        // 1. Remove 0..99 from a Set<Short> using an int expression
        Set<Short> shorts = new HashSet<>();
        for (short i = 0; i < 100; i++) {
            shorts.add(i);
            shorts.remove(i - 1); // noncompliant: i - 1 is an int, boxed to Integer, never equal to a Short
        }
        System.out.println("noncompliant: set size after add/remove loop = " + shorts.size());

        Set<Short> fixed = new HashSet<>();
        for (short i = 0; i < 100; i++) {
            fixed.add(i);
            fixed.remove((short) (i - 1)); // compliant: same type as the elements
        }
        System.out.println("compliant:    set size after add/remove loop = " + fixed.size());

        // 2. A revocation list keyed by long session ids
        Set<Long> revoked = new HashSet<>(Set.of(4242L));
        int sessionFromRequest = 4242;
        System.out.println("noncompliant: session 4242 revoked? " + revoked.contains(sessionFromRequest));
        System.out.println("compliant:    session 4242 revoked? " + isRevoked(revoked, sessionFromRequest));

        // 3. Map lookups have the same trap
        Map<Long, String> owners = new HashMap<>(Map.of(7L, "alice"));
        int doc = 7;
        System.out.println("noncompliant: owner of doc 7 = " + owners.get(doc));
        System.out.println("compliant:    owner of doc 7 = " + owners.get((long) doc));
    }

    // A typed wrapper turns the mistake into a compile-time conversion
    static boolean isRevoked(Set<Long> revoked, long sessionId) {
        return revoked.contains(sessionId);
    }
}

/* Output:
noncompliant: set size after add/remove loop = 100
compliant:    set size after add/remove loop = 1
noncompliant: session 4242 revoked? false
compliant:    session 4242 revoked? true
noncompliant: owner of doc 7 = null
compliant:    owner of doc 7 = alice
*/
