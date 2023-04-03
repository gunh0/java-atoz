// EXP03-J. Do not use the equality operators when comparing values of boxed primitives
// https://wiki.sei.cmu.edu/confluence/display/java/EXP03-J.+Do+not+use+the+equality+operators+when+comparing+values+of+boxed+primitives
//
// == and != on two Integer (Long, Short, Character, ...) objects compare
// references, not values. Autoboxing goes through valueOf(), which caches
// small values (Integer: at least -128..127), so == "works" in tests with
// small numbers and then fails in production once the values grow. Compare
// boxed values with equals(), Integer.compare(), or by unboxing explicitly
// (intValue()). Note that <, <=, > and >= do unbox, which makes the mix of
// operators even more misleading.
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class EXP03_BoxedEquality {
    public static void main(String[] args) {
        // Owner IDs of documents, and the ID of the requesting user, as stored in maps
        Map<String, Integer> ownerOf = new HashMap<>();
        ownerOf.put("small-doc", 42);
        ownerOf.put("big-doc", 4242);
        Map<String, Integer> session = new HashMap<>();
        session.put("bob", 42);
        session.put("carol", 4242);

        for (String[] request : new String[][] {{"bob", "small-doc"}, {"carol", "big-doc"}}) {
            Integer user = session.get(request[0]);
            Integer owner = ownerOf.get(request[1]);
            System.out.println(request[0] + " opens " + request[1]
                    + ": noncompliant " + (canEditNoncompliant(user, owner) ? "allowed" : "denied")
                    + ", compliant " + (canEditCompliant(user, owner) ? "allowed" : "denied"));
        }

        Integer a = 4242, b = 4242;
        System.out.println("a == b: " + (a == b) + ", a <= b && a >= b: " + (a <= b && a >= b) + ", a.equals(b): " + a.equals(b));
    }

    // Noncompliant: reference comparison; only true for cached small values
    static boolean canEditNoncompliant(Integer user, Integer owner) {
        return user == owner;
    }

    // Compliant: value comparison (Objects.equals also handles null)
    static boolean canEditCompliant(Integer user, Integer owner) {
        return Objects.equals(user, owner);
    }
}

/* Output:
bob opens small-doc: noncompliant allowed, compliant allowed
carol opens big-doc: noncompliant denied, compliant allowed
a == b: false, a <= b && a >= b: true, a.equals(b): true
*/
