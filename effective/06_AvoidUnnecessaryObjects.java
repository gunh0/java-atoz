// Effective Java, Item 6: Avoid creating unnecessary objects.
//
// Reuse an object instead of creating a functionally equivalent one whenever
// it is immutable or otherwise safe to share. Typical offenders:
//  - new String("literal") and new Boolean(...) instead of the literal / valueOf
//  - String.matches(regex) in a hot path, which compiles a Pattern every call
//  - accidental autoboxing (Long sum += long) creating one object per iteration
// This is not a call for object pools: small, short-lived objects are cheap.
// Only cache what is expensive to build or what is created in a loop.
import java.util.regex.Pattern;

public class AvoidUnnecessaryObjects {
    public static void main(String[] args) {
        // 1. Literals and static factories reuse instances; constructors cannot
        String literal = "bikini";
        String copy = new String("bikini");          // a new object every time
        System.out.println("literal reused: " + (literal == "bikini"));
        System.out.println("new String reused: " + (copy == literal));
        System.out.println("Boolean.valueOf reused: " + (Boolean.valueOf("true") == Boolean.TRUE));
        System.out.println("Integer.valueOf(127) cached: " + (Integer.valueOf(127) == Integer.valueOf(127)));
        System.out.println("Long.valueOf(128) cached: " + (Long.valueOf(128) == Long.valueOf(128)));

        // 2. Expensive objects: compile the regex once, keep it in a constant
        String[] inputs = {"MCMLXXVI", "MMXXI", "HELLO", "IIII"};
        for (String s : inputs) {
            boolean slow = RomanNumerals.isRomanNumeralSlow(s);
            boolean fast = RomanNumerals.isRomanNumeral(s);
            System.out.println(s + " is a Roman numeral: " + fast + (slow == fast ? "" : " (mismatch!)"));
        }

        // 3. Autoboxing: same result, very different allocation profile
        System.out.println("boxed sum:     " + boxedSum(100_000));
        System.out.println("primitive sum: " + primitiveSum(100_000));
    }

    // Long sum: every += unboxes, adds, and boxes a NEW Long (for values > 127)
    private static long boxedSum(int n) {
        Long sum = 0L;
        for (long i = 0; i < n; i++) {
            sum += i;
        }
        return sum;
    }

    // long sum: no objects at all. Prefer primitives to boxed primitives.
    private static long primitiveSum(int n) {
        long sum = 0L;
        for (long i = 0; i < n; i++) {
            sum += i;
        }
        return sum;
    }
}

final class RomanNumerals {
    private static final String REGEX = "^(?=.)M*(C[MD]|D?C{0,3})(X[CL]|L?X{0,3})(I[XV]|V?I{0,3})$";

    // Compiled once, when the class is initialized, then shared (Pattern is immutable)
    private static final Pattern ROMAN = Pattern.compile(REGEX);

    private RomanNumerals() {
    }

    // Anti-pattern: String.matches creates a new Pattern (a finite state
    // machine) on each call and throws it away immediately
    static boolean isRomanNumeralSlow(String s) {
        return s.matches(REGEX);
    }

    static boolean isRomanNumeral(String s) {
        return ROMAN.matcher(s).matches();
    }
}

/* Output:
literal reused: true
new String reused: false
Boolean.valueOf reused: true
Integer.valueOf(127) cached: true
Long.valueOf(128) cached: false
MCMLXXVI is a Roman numeral: true
MMXXI is a Roman numeral: true
HELLO is a Roman numeral: false
IIII is a Roman numeral: false
boxed sum:     4999950000
primitive sum: 4999950000
*/
