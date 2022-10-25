// Effective Java, Item 32: Combine generics and varargs judiciously.
//
// A varargs parameter is backed by an array, and an array of a generic type
// (List<String>[]) cannot be checked at run time. That allows heap
// pollution: a variable of a parameterized type that refers to an object of
// another type, which turns into a ClassCastException somewhere else. A
// generic varargs method is safe only if it (1) stores nothing into the
// varargs array and (2) never lets the array (or a clone) escape to
// untrusted code. Mark such methods @SafeVarargs. Otherwise take a List
// parameter instead and let callers use List.of.
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class GenericsAndVarargs {
    @SuppressWarnings("unchecked") // the calls below are unsafe on purpose
    public static void main(String[] args) {
        // 1. Storing into the generic varargs array
        try {
            dangerous(List.of("there be dragons!"));
        } catch (ClassCastException e) {
            System.out.println("dangerous(): " + e.getClass().getSimpleName());
        }

        // 2. Letting the varargs array escape: its runtime type is Object[]
        try {
            String[] attributes = pickTwo("good", "fast", "cheap");
            System.out.println("pickTwo: " + attributes.length);
        } catch (ClassCastException e) {
            System.out.println("pickTwo(): " + e.getClass().getSimpleName() + " (Object[] is not String[])");
        }

        // Safe: @SafeVarargs method only reads the array
        List<Integer> flat = flatten(List.of(1, 2), List.of(3), List.of(4, 5));
        System.out.println("flatten: " + flat);

        // Alternative: a List parameter, no varargs array at all
        System.out.println("flattenList: " + flattenList(List.of(List.of("a"), List.of("b", "c"))));
    }

    // Unsafe: heap pollution
    @SuppressWarnings("unchecked") // the anti-pattern, kept for the demo
    static void dangerous(List<String>... stringLists) {
        List<Integer> intList = List.of(42);
        Object[] objects = stringLists;
        objects[0] = intList;              // heap pollution
        String s = stringLists[0].get(0);  // ClassCastException
        System.out.println(s);
    }

    // Unsafe: exposes its generic parameter array
    @SuppressWarnings("unchecked")
    static <T> T[] toArray(T... args) {
        return args;
    }

    @SuppressWarnings("unchecked")
    static <T> T[] pickTwo(T a, T b, T c) {
        // The compiler creates an Object[] here, since T is erased
        switch (ThreadLocalRandom.current().nextInt(3)) {
            case 0:
                return toArray(a, b);
            case 1:
                return toArray(a, c);
            default:
                return toArray(b, c);
        }
    }

    @SafeVarargs
    static <T> List<T> flatten(List<? extends T>... lists) {
        List<T> result = new ArrayList<>();
        for (List<? extends T> list : lists) {
            result.addAll(list);
        }
        return result;
    }

    static <T> List<T> flattenList(List<List<? extends T>> lists) {
        List<T> result = new ArrayList<>();
        for (List<? extends T> list : lists) {
            result.addAll(list);
        }
        return result;
    }
}

/* Output:
dangerous(): ClassCastException
pickTwo(): ClassCastException (Object[] is not String[])
flatten: [1, 2, 3, 4, 5]
flattenList: [a, b, c]
*/
