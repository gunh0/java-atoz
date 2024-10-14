// System.identityHashCode: what it is, and what it is not
//
// Object.hashCode() (when not overridden) and System.identityHashCode(obj)
// return a per-object number chosen by the JVM the first time it is asked
// for, then stored in the object header, so it stays the same for the
// object's whole life even though the GC moves objects around. It is NOT an
// address and NOT unique: HotSpot draws it from a pseudo-random generator
// with 31 bits, so among a few hundred thousand objects some share a value
// (birthday bound). Use it for identity-based structures (IdentityHashMap,
// cycle detection while serializing), never as an object ID, a security
// token or a sort key. The values differ from run to run, so only facts
// about them are printed. (Locking on an object or asking for its identity
// hash also has a small cost: both use the header word.)
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class IdentityHashCode {
    enum Color { RED }

    public static void main(String[] args) {
        Object o = new Object();
        int h = System.identityHashCode(o);
        System.gc(); // objects may move; the identity hash does not change
        System.out.println("stable across calls and GC: " + (h == System.identityHashCode(o) && h == o.hashCode()));
        System.out.println("identityHashCode(null) = " + System.identityHashCode(null));

        // Independent of an overridden hashCode()
        String a = new String("same"), b = new String("same");
        System.out.println("equal strings: hashCode equal: " + (a.hashCode() == b.hashCode())
                + ", identity hashes equal: " + (System.identityHashCode(a) == System.identityHashCode(b)));
        System.out.println("enum hashCode is the identity hash: " + (Color.RED.hashCode() == System.identityHashCode(Color.RED)));

        // IdentityHashMap compares keys with ==, HashMap with equals()
        Map<String, Integer> byEquals = new HashMap<>();
        Map<String, Integer> byIdentity = new IdentityHashMap<>();
        for (String s : new String[] {a, b}) {
            byEquals.put(s, 1);
            byIdentity.put(s, 1);
        }
        System.out.println("HashMap size: " + byEquals.size() + ", IdentityHashMap size: " + byIdentity.size());

        // Not unique: find two live objects with the same identity hash
        Map<Integer, Object> seen = new HashMap<>();
        Object[] keepAlive = new Object[500_000];
        int created = 0;
        Object[] pair = null;
        while (pair == null && created < keepAlive.length) {
            Object x = new Object();
            keepAlive[created++] = x;
            Object previous = seen.putIfAbsent(System.identityHashCode(x), x);
            if (previous != null) pair = new Object[] {previous, x};
        }
        System.out.println("found two distinct live objects with the same identity hash: "
                + (pair != null && pair[0] != pair[1]) + " (within " + keepAlive.length + " objects)");
        System.out.println("identity hashes are non-negative 31-bit values: "
                + seen.keySet().stream().allMatch(v -> v >= 0));
    }
}

/* Output:
stable across calls and GC: true
identityHashCode(null) = 0
equal strings: hashCode equal: true, identity hashes equal: false
enum hashCode is the identity hash: true
HashMap size: 1, IdentityHashMap size: 2
found two distinct live objects with the same identity hash: true (within 500000 objects)
identity hashes are non-negative 31-bit values: true
*/
