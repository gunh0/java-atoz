// A varargs parameter (String... names) accepts zero or more arguments; the
// compiler packs them into an array, so inside the method it IS an array.
//  - only the last parameter can be varargs; require "at least one" with a
//    normal first parameter: max(int first, int... rest)
//  - you can pass an existing array directly; passing null passes a null
//    array, not an array holding null
//  - overload resolution prefers fixed-arity methods over varargs ones
//  - a generic varargs parameter (T...) creates a T[] whose real type is
//    Object[] (heap pollution warning); @SafeVarargs promises the method only
//    reads the array and never lets it escape
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VarargsExample {

    static int sum(int... numbers) {
        int total = 0;
        for (int n : numbers) {
            total += n;
        }
        return total;
    }

    static int max(int first, int... rest) {
        int max = first;
        for (int n : rest) {
            max = Math.max(max, n);
        }
        return max;
    }

    static String describe(String... names) {
        if (names == null) {
            return "null array";
        }
        return names.length + " arg(s) " + Arrays.toString(names);
    }

    static String pick(Object o) {
        return "pick(Object)";
    }

    static String pick(Object... os) {
        return "pick(Object...)";
    }

    @SafeVarargs
    static <T> List<T> listOf(T... items) {
        return new ArrayList<>(Arrays.asList(items)); // only reads the array: safe
    }

    // Unsafe: returns the generic array itself, whose runtime type is Object[]
    @SuppressWarnings("unchecked")
    static <T> T[] toArray(T... items) {
        return items;
    }

    @SuppressWarnings("unchecked") // silences the javac warning that predicts this bug
    static <T> T[] pair(T a, T b) {
        return toArray(a, b); // here T[] is created as Object[]
    }

    public static void main(String[] args) {
        System.out.println("sum() = " + sum());
        System.out.println("sum(1, 2, 3) = " + sum(1, 2, 3));
        System.out.println("sum(new int[]{4, 5}) = " + sum(new int[] {4, 5}));
        System.out.println("max(7) = " + max(7) + ", max(3, 9, 4) = " + max(3, 9, 4));

        System.out.println(describe());
        System.out.println(describe("alice", "bob"));
        System.out.println(describe((String) null));   // one null element
        System.out.println(describe((String[]) null)); // the array itself is null

        System.out.println("pick(\"x\") -> " + pick("x"));
        System.out.println("pick(\"x\", \"y\") -> " + pick("x", "y"));

        List<String> names = listOf("carol", "dave");
        names.add("erin");
        System.out.println("listOf: " + names);

        String[] direct = toArray("a", "b"); // fine: the compiler creates a String[]
        System.out.println("direct toArray type: " + direct.getClass().getSimpleName());
        try {
            String[] viaGeneric = pair("a", "b"); // Object[] cast to String[]
            System.out.println(viaGeneric.length);
        } catch (ClassCastException e) {
            System.out.println("pair(\"a\", \"b\") -> ClassCastException (heap pollution)");
        }
    }
}

/* Output:
sum() = 0
sum(1, 2, 3) = 6
sum(new int[]{4, 5}) = 9
max(7) = 7, max(3, 9, 4) = 9
0 arg(s) []
2 arg(s) [alice, bob]
1 arg(s) [null]
null array
pick("x") -> pick(Object)
pick("x", "y") -> pick(Object...)
listOf: [carol, dave, erin]
direct toArray type: String[]
pair("a", "b") -> ClassCastException (heap pollution)
*/
