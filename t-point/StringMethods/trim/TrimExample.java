// trim() removes leading and trailing characters whose code is <= U+0020:
// the space and every ASCII control character (\t, \n, \r, even \0).
// strip() (Java 11) removes leading and trailing Unicode WHITESPACE as
// defined by Character.isWhitespace() instead. The two sets overlap but are
// not the same:
//  - only trim() removes NUL and other non-whitespace control characters
//  - only strip() removes Unicode spaces such as U+3000 IDEOGRAPHIC SPACE
//  - neither removes U+00A0 NO-BREAK SPACE (it is not "whitespace" in Java)
// Both return the same String object when there is nothing to remove.
public class TrimExample {
    public static void main(String[] args) {
        // Everyday use: clean up user input before validating it
        String input = "  \talice@example.com \n";
        System.out.println("[" + input.trim() + "]");
        System.out.println("[" + input.strip() + "]");

        // Only the ends are touched, inner spaces stay
        System.out.println("[" + "  John   Smith  ".trim() + "]");

        // Where the two differ
        compare("NUL padding", "\0id-42\0");
        compare("ideographic space", "\u3000id-42\u3000");
        compare("no-break space", "\u00A0id-42\u00A0");

        // One-sided variants exist only for strip
        System.out.println("stripLeading:  [" + "  left  ".stripLeading() + "]");
        System.out.println("stripTrailing: [" + "  right  ".stripTrailing() + "]");

        // Nothing to trim: no copy is made
        String clean = "token";
        System.out.println("same instance: " + (clean == clean.trim()) + " " + (clean == clean.strip()));

        // A blank string trims down to the empty string
        System.out.println("blank -> length " + " \t\n ".trim().length());
    }

    private static void compare(String label, String s) {
        System.out.printf("%-18s length %d, trim() -> %d, strip() -> %d%n",
                label, s.length(), s.trim().length(), s.strip().length());
    }
}

/* Output:
[alice@example.com]
[alice@example.com]
[John   Smith]
NUL padding        length 7, trim() -> 5, strip() -> 7
ideographic space  length 7, trim() -> 7, strip() -> 5
no-break space     length 7, trim() -> 7, strip() -> 7
stripLeading:  [left  ]
stripTrailing: [  right]
same instance: true true
blank -> length 0
*/
