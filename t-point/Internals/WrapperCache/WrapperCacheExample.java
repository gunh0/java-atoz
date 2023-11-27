// Integer.valueOf(int) (which autoboxing calls) returns cached Integer objects
// for -128..127 and creates a new object outside that range. The same holds
// for Short, Long, Byte (all values) and Character (0..127); Boolean has just
// TRUE and FALSE. Float and Double are never cached.
// The upper bound for Integer can be raised with
// -XX:AutoBoxCacheMax=<n> (or java.lang.Integer.IntegerCache.high), so code
// whose behaviour depends on the cache is not even portable between JVM runs.
// Consequence: == on boxed numbers compares identity and "works" only for
// small values. Use equals(), compare unboxed ints, or Integer.compare().
// new Integer(...) bypasses the cache; it is deprecated for removal.
import java.util.List;

public class WrapperCacheExample {
    public static void main(String[] args) {
        for (int v : new int[] {100, 127, 128, 1000, -128, -129}) {
            Integer a = v; // Integer.valueOf(v)
            Integer b = v;
            System.out.printf("Integer %5d: a == b -> %-5b a.equals(b) -> %b%n", v, a == b, a.equals(b));
        }

        Long l1 = 127L, l2 = 127L, l3 = 128L, l4 = 128L;
        System.out.println("Long 127 ==: " + (l1 == l2) + ", Long 128 ==: " + (l3 == l4));
        Character c1 = 'A', c2 = 'A';
        System.out.println("Character 'A' ==: " + (c1 == c2));
        Boolean t1 = true, t2 = Boolean.valueOf("TRUE");
        System.out.println("Boolean true ==: " + (t1 == t2));
        Double d1 = 1.0, d2 = 1.0;
        System.out.println("Double 1.0 ==: " + (d1 == d2) + " (Double is never cached)");

        // A realistic bug: comparing IDs that come back as boxed values
        List<Integer> adminIds = List.of(7, 1001);
        Integer currentUser = 1001;
        boolean byIdentity = false, byEquals = false;
        for (Integer id : adminIds) {
            byIdentity |= id == currentUser;
            byEquals |= id.equals(currentUser);
        }
        System.out.println("user 1001 is admin? == says " + byIdentity + ", equals says " + byEquals);

        // Mixing a boxed and a primitive operand unboxes, so this is a value compare
        int primitive = 1001;
        System.out.println("Integer 1001 == int 1001: " + (currentUser == primitive));
    }
}

/* Output:
Integer   100: a == b -> true  a.equals(b) -> true
Integer   127: a == b -> true  a.equals(b) -> true
Integer   128: a == b -> false a.equals(b) -> true
Integer  1000: a == b -> false a.equals(b) -> true
Integer  -128: a == b -> true  a.equals(b) -> true
Integer  -129: a == b -> false a.equals(b) -> true
Long 127 ==: true, Long 128 ==: false
Character 'A' ==: true
Boolean true ==: true
Double 1.0 ==: false (Double is never cached)
user 1001 is admin? == says false, equals says true
Integer 1001 == int 1001: true
*/
