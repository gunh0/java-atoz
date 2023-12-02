// Autoboxing converts int <-> Integer (and friends) silently. Convenient, but
// each conversion is a method call that can throw or allocate:
//  - unboxing null throws NullPointerException (Map.get of a missing key,
//    a null field, a ternary that mixes Integer and int)
//  - List<Integer>.remove(1) calls remove(int index), not remove(Object)
//  - boxed arithmetic in a loop creates garbage: Long sum += i allocates
//    a new Long almost every iteration
//  - overloads: a primitive argument prefers widening (int -> long) over
//    boxing (int -> Integer)
// See Internals/WrapperCache for == on boxed values.
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AutoboxingExample {
    static String describe(long x) { return "long overload"; }
    static String describe(Integer x) { return "Integer overload"; }

    static Integer lookupLimit(Map<String, Integer> limits, String user) {
        return limits.get(user);
    }

    public static void main(String[] args) {
        // 1. Unboxing null
        Map<String, Integer> loginAttempts = new HashMap<>(Map.of("alice", 2));
        try {
            int bob = loginAttempts.get("bob"); // get -> null -> null.intValue()
            System.out.println(bob);
        } catch (NullPointerException e) {
            System.out.println("1. int x = map.get(missing) -> NullPointerException");
        }
        System.out.println("   getOrDefault avoids it: " + loginAttempts.getOrDefault("bob", 0));

        // 2. Conditional expression: Integer and int operands -> type is int
        Map<String, Integer> limits = new HashMap<>();
        boolean premium = false;
        try {
            Integer limit = premium ? 1000 : lookupLimit(limits, "guest"); // unboxes the null
            System.out.println(limit);
        } catch (NullPointerException e) {
            System.out.println("2. cond ? 1000 : (Integer) null -> NullPointerException");
        }
        Integer limit = premium ? Integer.valueOf(1000) : lookupLimit(limits, "guest");
        System.out.println("   with both operands Integer: " + limit);

        // 3. remove(int) vs remove(Object)
        List<Integer> ports = new ArrayList<>(List.of(22, 80, 1, 443));
        ports.remove(1);                    // removes index 1 (80)
        System.out.println("3. remove(1) -> " + ports);
        ports.remove(Integer.valueOf(1));   // removes the value 1
        System.out.println("   remove(Integer.valueOf(1)) -> " + ports);

        // 4. Overload resolution: widening beats boxing
        System.out.println("4. describe(42) picks the " + describe(42));

        // 5. Boxed accumulator: same result, many temporary objects
        Long boxedSum = 0L;
        long primitiveSum = 0L;
        for (int i = 0; i < 100_000; i++) {
            boxedSum += i;      // boxedSum = Long.valueOf(boxedSum.longValue() + i)
            primitiveSum += i;
        }
        System.out.println("5. sums equal: " + (boxedSum == primitiveSum)
                + " (but the boxed loop allocated ~100,000 Long objects)");

        // 6. Comparing a boxed value with a primitive unboxes (NPE if null)
        Integer maybe = null;
        try {
            System.out.println(maybe == 0);
        } catch (NullPointerException e) {
            System.out.println("6. (Integer) null == 0 -> NullPointerException");
        }
    }
}

/* Output:
1. int x = map.get(missing) -> NullPointerException
   getOrDefault avoids it: 0
2. cond ? 1000 : (Integer) null -> NullPointerException
   with both operands Integer: null
3. remove(1) -> [22, 1, 443]
   remove(Integer.valueOf(1)) -> [22, 443]
4. describe(42) picks the long overload
5. sums equal: true (but the boxed loop allocated ~100,000 Long objects)
6. (Integer) null == 0 -> NullPointerException
*/
