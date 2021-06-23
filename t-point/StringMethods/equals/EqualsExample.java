// equals() compares the characters of two strings; == compares references.
// equalsIgnoreCase() ignores case differences, and contentEquals() compares a
// String with any CharSequence such as a StringBuilder. Two literals with the
// same text share one interned instance, which is why == sometimes "works":
// never rely on that, because strings built at run time are new objects.
import java.util.Objects;

public class EqualsExample {
    public static void main(String[] args) {
        String a = "hello";
        String b = "hello";                 // same literal: same interned object
        String c = new String("hello");     // explicitly a new object
        String d = "hel" + "lo".toString(); // computed at run time: new object

        System.out.println("a.equals(c): " + a.equals(c)); // true
        System.out.println("a == b: " + (a == b));         // true (interning)
        System.out.println("a == c: " + (a == c));         // false
        System.out.println("a == d: " + (a == d));         // false
        System.out.println("a == c.intern(): " + (a == c.intern())); // true

        // Case-insensitive comparison
        System.out.println("\"HELLO\".equals(a): " + "HELLO".equals(a));
        System.out.println("\"HELLO\".equalsIgnoreCase(a): " + "HELLO".equalsIgnoreCase(a));

        // equals() is false for a StringBuilder, even with the same text
        StringBuilder sb = new StringBuilder("hello");
        System.out.println("a.equals(sb): " + a.equals(sb));
        System.out.println("a.contentEquals(sb): " + a.contentEquals(sb));

        // Null-safe comparisons: put the constant first, or use Objects.equals
        String input = null;
        System.out.println("\"yes\".equals(input): " + "yes".equals(input));
        System.out.println("Objects.equals(input, null): " + Objects.equals(input, null));
        try {
            input.equals("yes");
        } catch (NullPointerException e) {
            System.out.println("input.equals(\"yes\") throws NullPointerException");
        }
    }
}

/* Output:
a.equals(c): true
a == b: true
a == c: false
a == d: false
a == c.intern(): true
"HELLO".equals(a): false
"HELLO".equalsIgnoreCase(a): true
a.equals(sb): false
a.contentEquals(sb): true
"yes".equals(input): false
Objects.equals(input, null): true
input.equals("yes") throws NullPointerException
*/
