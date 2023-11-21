// Java 21 — String.splitWithDelimiters and Pattern.splitWithDelimiters
// (API addition, no JEP)
//
// split() throws the delimiters away. That is fine for CSV, but not when
// the delimiter carries meaning: operators in an arithmetic expression,
// the separators of a path or query string you want to rebuild, the
// whitespace runs a formatter should preserve. The workarounds were a
// Matcher loop with find()/start()/end(), or look-around tricks such as
// split("(?<=[+-])|(?=[+-])") that are hard to read and easy to get
// wrong. Java 21 adds splitWithDelimiters(regex, limit): the result
// alternates text and the matched delimiters, so joining it gives back the
// original string exactly.
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SplitWithDelimiters {
    public static void main(String[] args) {
        String expr = "12+7-3*4";

        // split() loses the operators
        System.out.println("split:                " + Arrays.toString(expr.split("[-+*/]")));

        // Before: a Matcher loop that copies text and delimiters by hand
        List<String> tokens = new ArrayList<>();
        Matcher m = Pattern.compile("[-+*/]").matcher(expr);
        int last = 0;
        while (m.find()) {
            tokens.add(expr.substring(last, m.start()));
            tokens.add(m.group());
            last = m.end();
        }
        tokens.add(expr.substring(last));
        System.out.println("before (Matcher):     " + tokens);

        // Before: look-around split, cryptic
        System.out.println("before (look-around): " + Arrays.toString(expr.split("(?<=[-+*/])|(?=[-+*/])")));

        // After
        String[] parts = expr.splitWithDelimiters("[-+*/]", 0);
        System.out.println("splitWithDelimiters:  " + Arrays.toString(parts));
        System.out.println("evaluated left to right: " + evaluate(parts));

        // Delimiters that vary in length are kept exactly, so the input can be rebuilt
        String messy = "a,  b,c ,d";
        String[] kept = messy.splitWithDelimiters("\\s*,\\s*", 0);
        System.out.println("kept: " + Arrays.toString(kept) + ", rejoined equals input: " + String.join("", kept).equals(messy));

        // The limit counts text parts, like split(): limit 2 = first field + delimiter + rest
        System.out.println("limit 2: " + Arrays.toString("k=v=w".splitWithDelimiters("=", 2)));

        // Pattern variant, e.g. to rewrite query-string values but keep the separators
        Pattern sep = Pattern.compile("[&=]");
        String[] query = sep.splitWithDelimiters("user=bob&token=s3cr3t&lang=en", 0);
        for (int i = 2; i < query.length; i += 4) {
            if (query[i - 2].equals("token")) {
                query[i] = "***";
            }
        }
        System.out.println("redacted: " + String.join("", query));
    }

    static int evaluate(String[] parts) {
        int acc = Integer.parseInt(parts[0]);
        for (int i = 1; i < parts.length; i += 2) {
            int n = Integer.parseInt(parts[i + 1]);
            acc = switch (parts[i]) {
                case "+" -> acc + n;
                case "-" -> acc - n;
                case "*" -> acc * n;
                default -> acc / n;
            };
        }
        return acc;
    }
}

/* Output:
split:                [12, 7, 3, 4]
before (Matcher):     [12, +, 7, -, 3, *, 4]
before (look-around): [12, +, 7, -, 3, *, 4]
splitWithDelimiters:  [12, +, 7, -, 3, *, 4]
evaluated left to right: 64
kept: [a, ,  , b, ,, c,  ,, d], rejoined equals input: true
limit 2: [k, =, v=w]
redacted: user=bob&token=***&lang=en
*/
