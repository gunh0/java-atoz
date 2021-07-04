// replace() swaps every occurrence of a char or a literal CharSequence.
// replaceAll() and replaceFirst() take a REGULAR EXPRESSION, and in their
// replacement string $1, $2... refer to captured groups while \ escapes.
// Mixing them up is a classic bug: "a.b".replaceAll(".", "-") replaces every
// character, because "." is the regex "any character". Use Pattern.quote and
// Matcher.quoteReplacement when the text comes from user input.
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReplaceExample {
    public static void main(String[] args) {
        String version = "1.2.3";

        // Literal replacement
        System.out.println(version.replace('.', '-'));        // 1-2-3
        System.out.println(version.replace(".", " dot "));    // 1 dot 2 dot 3

        // Regex replacement: "." matches any character
        System.out.println(version.replaceAll(".", "-"));     // -----
        System.out.println(version.replaceAll("\\.", "-"));   // 1-2-3
        System.out.println(version.replaceFirst("\\.", "-")); // 1-2.3

        // Regex power: collapse whitespace, reorder with groups
        System.out.println("too    many \t spaces".replaceAll("\\s+", " "));
        System.out.println("2021-07-04".replaceAll("(\\d{4})-(\\d{2})-(\\d{2})", "$3/$2/$1"));

        // Untrusted input: quote both the pattern and the replacement
        String userPattern = "$5.00";
        String userReplacement = "$10.00";
        String price = "Price: $5.00";
        System.out.println(price.replaceAll(Pattern.quote(userPattern), Matcher.quoteReplacement(userReplacement)));
        try {
            price.replaceAll(Pattern.quote(userPattern), userReplacement); // "$1" means group 1
        } catch (IndexOutOfBoundsException e) {
            System.out.println("unquoted replacement failed: " + e.getMessage());
        }
        // ...or simply use replace(), which is literal on both sides
        System.out.println(price.replace(userPattern, userReplacement));

        // Strings are immutable: the original is unchanged
        System.out.println(version);
    }
}

/* Output:
1-2-3
1 dot 2 dot 3
-----
1-2-3
1-2.3
too many spaces
04/07/2021
Price: $10.00
unquoted replacement failed: No group 1
Price: $10.00
1.2.3
*/
