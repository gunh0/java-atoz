// Effective Java, Item 61: Prefer primitive types to boxed primitives.
//
// int and Integer look interchangeable thanks to autoboxing, but they differ
// in three ways that bite: a boxed value has identity, so == compares
// references, not values; a boxed value can be null, and unboxing null
// throws NullPointerException; and boxing allocates, so a loop that
// accumulates into a Long creates an object per iteration. Use primitives
// unless you must: as elements, keys and values of collections, as type
// arguments, and for reflective calls. When mixing them in one expression,
// remember that the boxed operand is auto-unboxed.
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PrimitivesOverBoxed {
    static Integer i; // a field: defaults to null, not 0

    public static void main(String[] args) {
        // Broken comparator: == on two Integer objects compares identity
        Comparator<Integer> naturalOrder = (a, b) -> (a < b) ? -1 : (a == b ? 0 : 1);
        Integer x = Integer.valueOf(1000); // outside the small-value cache: distinct objects
        Integer y = Integer.valueOf(1000);
        System.out.println("broken compare(1000, 1000) = " + naturalOrder.compare(x, y));

        Comparator<Integer> fixed = (a, b) -> {
            int ia = a, ib = b; // unbox once, then compare values
            return (ia < ib) ? -1 : (ia == ib ? 0 : 1);
        };
        System.out.println("fixed  compare(1000, 1000) = " + fixed.compare(x, y));
        System.out.println("Comparator.naturalOrder()  = " + Comparator.<Integer>naturalOrder().compare(x, y));

        // Small values hide the bug because of the Integer cache
        System.out.println("Integer.valueOf(100) == Integer.valueOf(100): " + (Integer.valueOf(100) == Integer.valueOf(100)));
        System.out.println("Integer.valueOf(1000) == Integer.valueOf(1000): " + (x == y));

        // Unboxing a null throws
        try {
            if (i == 42) {
                System.out.println("unbelievable");
            }
        } catch (NullPointerException e) {
            System.out.println("i == 42 with null Integer: NullPointerException");
        }

        // Boxed accumulator: correct result, but one new Long per iteration
        Long boxedSum = 0L;
        long primitiveSum = 0L;
        for (long n = 0; n < 1_000_000; n++) {
            boxedSum += n;   // unbox, add, box a new Long
            primitiveSum += n;
        }
        System.out.println("sums equal: " + boxedSum.equals(primitiveSum) + " (" + primitiveSum + ")");

        // Collections need boxes; that is a legitimate use
        List<Integer> list = new ArrayList<>(List.of(3, 1, 2));
        list.sort(null);
        System.out.println("List<Integer>: " + list);
    }
}

/* Output:
broken compare(1000, 1000) = 1
fixed  compare(1000, 1000) = 0
Comparator.naturalOrder()  = 0
Integer.valueOf(100) == Integer.valueOf(100): true
Integer.valueOf(1000) == Integer.valueOf(1000): false
i == 42 with null Integer: NullPointerException
sums equal: true (499999500000)
List<Integer>: [1, 2, 3]
*/
