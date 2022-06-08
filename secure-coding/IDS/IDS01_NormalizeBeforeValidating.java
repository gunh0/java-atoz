// IDS01-J. Normalize strings before validating them
// https://wiki.sei.cmu.edu/confluence/display/java/IDS01-J.+Normalize+strings+before+validating+them
//
// Unicode often has several encodings for what is "the same" text. Under
// NFKC normalization, the SMALL LESS-THAN SIGN U+FE64 and the FULLWIDTH
// LESS-THAN SIGN U+FF1C both become the ASCII '<'. If a filter checks the
// raw input for '<' and the application normalizes AFTERWARDS (or a later
// component does, such as a database, a browser or a file system), the
// forbidden character slips past the check. Always normalize first (NFKC is
// the usual choice for identifiers and input validation), then validate the
// normalized form, and then use only that normalized form.
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.regex.Pattern;

public class IDS01_NormalizeBeforeValidating {
    private static final Pattern TAG = Pattern.compile("[<>]");

    public static void main(String[] args) {
        String[] inputs = {
                "hello",
                "<script>",                    // plain ASCII: caught either way
                "﹤script﹥",          // SMALL LESS-THAN / GREATER-THAN SIGN
                "＜script＞",          // FULLWIDTH LESS-THAN / GREATER-THAN SIGN
        };
        for (String input : inputs) {
            System.out.printf("input %-24s noncompliant: %-24s compliant: %s%n",
                    escape(input), noncompliant(input), compliant(input));
        }
    }

    // Noncompliant: validate the raw string, then normalize it
    static String noncompliant(String input) {
        if (TAG.matcher(input).find()) {
            return "rejected";
        }
        String normalized = Normalizer.normalize(input, Form.NFKC);
        return "accepted as " + normalized;
    }

    // Compliant: normalize first, then validate (and use) the normalized string
    static String compliant(String input) {
        String normalized = Normalizer.normalize(input, Form.NFKC);
        if (TAG.matcher(normalized).find()) {
            return "rejected";
        }
        return "accepted as " + normalized;
    }

    // Print non-ASCII characters as \\uXXXX so the output is plain ASCII
    static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(c < 128 ? String.valueOf(c) : String.format("\\u%04X", (int) c));
        }
        return sb.toString();
    }
}

/* Output:
input hello                    noncompliant: accepted as hello        compliant: accepted as hello
input <script>                 noncompliant: rejected                 compliant: rejected
input \uFE64script\uFE65       noncompliant: accepted as <script>     compliant: rejected
input \uFF1Cscript\uFF1E       noncompliant: accepted as <script>     compliant: rejected
*/
