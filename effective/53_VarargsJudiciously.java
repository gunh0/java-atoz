// Effective Java, Item 53: Use varargs judiciously.
//
// A varargs method accepts zero or more arguments by creating an array on
// every call. When the method needs at least one argument, declaring only
// "int... args" pushes the check to run time and makes the code ugly; take
// the required argument(s) as normal parameters followed by the varargs. In
// performance-critical code the array allocation can matter: the trick used
// by EnumSet.of and List.of is to provide fixed-arity overloads for the
// common small counts (0-3 or so) and one varargs overload for the rest.
import java.util.Arrays;

public class VarargsJudiciously {
    public static void main(String[] args) {
        System.out.println("sum() = " + sum() + ", sum(1, 2, 3) = " + sum(1, 2, 3));

        // Bad: zero arguments compile and fail at run time
        System.out.println("minBad(5, 2, 8) = " + minBad(5, 2, 8));
        try {
            minBad();
        } catch (IllegalArgumentException e) {
            System.out.println("minBad(): " + e.getMessage());
        }

        // Good: "at least one" is in the signature; min() does not compile
        System.out.println("min(5, 2, 8) = " + min(5, 2, 8));
        System.out.println("min(7) = " + min(7));
        // min(); // error: method min cannot be applied to given types

        // Overloads for the common cases, varargs for the tail
        Logger.log("a");
        Logger.log("a", "b");
        Logger.log("a", "b", "c");
        Logger.log("a", "b", "c", "d");
    }

    static int sum(int... args) {
        int sum = 0;
        for (int arg : args) sum += arg;
        return sum;
    }

    static int minBad(int... args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("too few arguments");
        }
        int min = args[0];
        for (int i = 1; i < args.length; i++) {
            if (args[i] < min) min = args[i];
        }
        return min;
    }

    static int min(int firstArg, int... remainingArgs) {
        int min = firstArg;
        for (int arg : remainingArgs) {
            if (arg < min) min = arg;
        }
        return min;
    }
}

class Logger {
    static void log(String a) {
        System.out.println("log(1 arg, no array): " + a);
    }

    static void log(String a, String b) {
        System.out.println("log(2 args, no array): " + a + b);
    }

    static void log(String a, String b, String c) {
        System.out.println("log(3 args, no array): " + a + b + c);
    }

    static void log(String a, String b, String c, String... rest) {
        System.out.println("log(varargs, array of " + rest.length + "): " + a + b + c + String.join("", rest)
                + " " + Arrays.toString(rest));
    }
}

/* Output:
sum() = 0, sum(1, 2, 3) = 6
minBad(5, 2, 8) = 2
minBad(): too few arguments
min(5, 2, 8) = 2
min(7) = 7
log(1 arg, no array): a
log(2 args, no array): ab
log(3 args, no array): abc
log(varargs, array of 1): abcd [d]
*/
