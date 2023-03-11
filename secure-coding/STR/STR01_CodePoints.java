// STR01-J. Do not assume that a Java char fully represents a Unicode code point
// https://wiki.sei.cmu.edu/confluence/display/java/STR01-J.+Do+not+assume+that+a+Java+char+fully+represents+a+Unicode+code+point
//
// A Java char is a UTF-16 code unit. Characters outside the Basic
// Multilingual Plane (emoji, many CJK ideographs, mathematical letters) take
// two chars, a surrogate pair. Code that inspects or cuts strings one char
// at a time classifies each half on its own (a lone surrogate is not a
// letter) and can split a pair, leaving a malformed string that later
// encoders turn into '?' or U+FFFD. Work with code points: codePointAt(),
// Character.charCount(), codePoints(), offsetByCodePoints().
import java.nio.charset.StandardCharsets;

public class STR01_CodePoints {
    public static void main(String[] args) {
        String name = "\uD835\uDC9Clice"; // MATHEMATICAL SCRIPT CAPITAL A + "lice"
        System.out.println("length in chars: " + name.length() + ", in code points: " + name.codePointCount(0, name.length()));
        System.out.println("noncompliant, all letters? " + allLettersNoncompliant(name));
        System.out.println("compliant,    all letters? " + allLettersCompliant(name));

        String tag = "go\uD83D\uDE80"; // "go" + ROCKET
        String cut = truncateNoncompliant(tag, 3);
        System.out.println("noncompliant truncate to 3: ends with a lone surrogate? "
                + Character.isSurrogate(cut.charAt(cut.length() - 1)) + ", after UTF-8 round trip: " + utf8RoundTrip(cut));
        String ok = truncateCompliant(tag, 3);
        System.out.println("compliant truncate to 3:    unchanged? " + ok.equals(tag) + ", after UTF-8 round trip: "
                + utf8RoundTrip(ok).equals(tag));
    }

    static String utf8RoundTrip(String s) {
        return new String(s.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    // Noncompliant: classifies each UTF-16 unit separately
    static boolean allLettersNoncompliant(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isLetter(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    // Compliant: steps through whole code points
    static boolean allLettersCompliant(String s) {
        return s.codePoints().allMatch(Character::isLetter);
    }

    // Noncompliant: counts chars, can stop in the middle of a surrogate pair
    static String truncateNoncompliant(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    // Compliant: counts code points
    static String truncateCompliant(String s, int max) {
        if (s.codePointCount(0, s.length()) <= max) {
            return s;
        }
        return s.substring(0, s.offsetByCodePoints(0, max));
    }
}

/* Output:
length in chars: 6, in code points: 5
noncompliant, all letters? false
compliant,    all letters? true
noncompliant truncate to 3: ends with a lone surrogate? true, after UTF-8 round trip: go?
compliant truncate to 3:    unchanged? true, after UTF-8 round trip: true
*/
