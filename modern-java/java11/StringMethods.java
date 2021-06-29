// Java 11 — String::isBlank, lines, strip, stripLeading, stripTrailing, repeat
// (API additions, no JEP)
//
// Java 11 filled long-standing gaps in String that used to need helper
// libraries or loops. strip() differs from the old trim(): trim() removes
// every char <= U+0020 (space and ASCII control characters), while strip()
// removes Unicode whitespace as defined by Character.isWhitespace, so it also
// handles characters such as U+2003 EM SPACE. isBlank() is the matching test,
// and lines() splits on \n, \r or \r\n lazily as a Stream.
import java.util.stream.Collectors;

public class StringMethods {
    public static void main(String[] args) {
        String padded = "\u2003 hello \u2003"; // EM SPACE + regular spaces

        // strip vs trim
        System.out.println("trim():  [" + visible(padded.trim()) + "]");
        System.out.println("strip(): [" + visible(padded.strip()) + "]");
        System.out.println("stripLeading():  [" + "  left".stripLeading() + "]");
        System.out.println("stripTrailing(): [" + "right  ".stripTrailing() + "]");

        // isBlank vs isEmpty
        String spaces = " \t\n";
        System.out.println("isEmpty(): " + spaces.isEmpty() + ", isBlank(): " + spaces.isBlank());

        // Before Java 11: spaces.trim().isEmpty(), which misses Unicode spaces
        System.out.println("EM SPACE, trim().isEmpty(): " + "\u2003".trim().isEmpty()
                + ", isBlank(): " + "\u2003".isBlank());

        // lines(): handles \n, \r\n and \r alike
        String text = "first\nsecond\r\nthird\rfourth";
        System.out.println("lines: " + text.lines().count());
        System.out.println(text.lines().map(String::toUpperCase).collect(Collectors.joining(", ")));

        // repeat(): before Java 11 you needed a loop or String.join + Collections.nCopies
        System.out.println("=".repeat(20));
        System.out.println("ab".repeat(3) + "|" + "x".repeat(0) + "|");
    }

    // Show the EM SPACE as an escape so the output is plain ASCII
    private static String visible(String s) {
        return s.replace("\u2003", "\\u2003");
    }
}

/* Output:
trim():  [\u2003 hello \u2003]
strip(): [hello]
stripLeading():  [left]
stripTrailing(): [right]
isEmpty(): false, isBlank(): true
EM SPACE, trim().isEmpty(): false, isBlank(): true
lines: 4
FIRST, SECOND, THIRD, FOURTH
====================
ababab||
*/
