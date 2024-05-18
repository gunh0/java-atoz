// Lookaround assertions check what comes before or after the current
// position without consuming characters, so the match itself stays small:
//   (?=X)   positive lookahead    X must follow
//   (?!X)   negative lookahead    X must not follow
//   (?<=X)  positive lookbehind   X must precede
//   (?<!X)  negative lookbehind   X must not precede
// Several lookaheads anchored at ^ express "contains A and B and C" in any
// order. Because they match an empty position, lookarounds also make good
// split() boundaries.
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LookaroundExample {
    public static void main(String[] args) {
        // Password policy: at least 12 chars, one digit, one lower, one upper, one symbol
        Pattern policy = Pattern.compile("^(?=.*\\d)(?=.*[a-z])(?=.*[A-Z])(?=.*[^\\w\\s]).{12,}$");
        for (String pw : List.of("short1A!", "alllowercase123!", "Correct-Horse-9", "NoDigitsHere!!x")) {
            System.out.printf("%-18s %s%n", pw, policy.matcher(pw).matches() ? "accepted" : "rejected");
        }

        // Insert thousands separators: a position not at a word boundary,
        // followed by groups of three digits up to the end of the number
        for (String n : List.of("1234567", "999", "1000", "12345678901")) {
            System.out.println(n + " -> " + n.replaceAll("\\B(?=(\\d{3})+(?!\\d))", ","));
        }

        // Positive lookbehind: amounts that follow a dollar sign, without the sign
        Matcher amounts = Pattern.compile("(?<=\\$)\\d+(?:\\.\\d{2})?").matcher("Paid $120.50, owed 30 EUR, refund $7");
        System.out.print("dollar amounts:");
        while (amounts.find()) {
            System.out.print(" " + amounts.group());
        }
        System.out.println();

        // Negative lookbehind / lookahead: "error" not preceded by "no " and not followed by "s"
        Matcher errors = Pattern.compile("(?<!no )\\berror\\b(?!s)").matcher("no error here; error found; errors listed");
        while (errors.find()) {
            System.out.println("real 'error' at index " + errors.start());
        }

        // Zero-width split: break camelCase and acronyms apart
        String[] parts = "parseHTTPResponseCode".split("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
        System.out.println("split camelCase: " + String.join(" ", parts));

        // Lookahead does not consume: both patterns match "foobar", but only the first includes "bar"
        System.out.println("foo(?=bar) in foobar matches: \"" + firstMatch("foo(?=bar)", "foobar") + "\"");
        System.out.println("foobar      in foobar matches: \"" + firstMatch("foobar", "foobar") + "\"");
    }

    static String firstMatch(String regex, String input) {
        Matcher m = Pattern.compile(regex).matcher(input);
        return m.find() ? m.group() : "";
    }
}

/* Output:
short1A!           rejected
alllowercase123!   rejected
Correct-Horse-9    accepted
NoDigitsHere!!x    rejected
1234567 -> 1,234,567
999 -> 999
1000 -> 1,000
12345678901 -> 12,345,678,901
dollar amounts: 120.50 7
real 'error' at index 15
split camelCase: parse HTTP Response Code
foo(?=bar) in foobar matches: "foo"
foobar      in foobar matches: "foobar"
*/
