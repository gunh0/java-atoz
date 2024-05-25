// String.replaceAll takes a replacement *string*, in which $1, ${name} and
// backslashes are special. That is a trap when the replacement comes from
// data: "$5" throws, "\" disappears.
//  - Matcher.replaceAll(Function<MatchResult, String>) (Java 9) computes each
//    replacement in code; the returned string is still interpreted, so wrap
//    data in Matcher.quoteReplacement.
//  - appendReplacement / appendTail give full control of the loop.
//  - Pattern.quote turns user text into a literal regex.
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReplaceFunctionExample {
    public static void main(String[] args) {
        // Compute each replacement
        Pattern word = Pattern.compile("\\b[a-z]");
        System.out.println(word.matcher("make every word title case").replaceAll(r -> r.group().toUpperCase()));

        Pattern number = Pattern.compile("\\d+");
        System.out.println(number.matcher("3 apples and 12 pears").replaceAll(r -> String.valueOf(Integer.parseInt(r.group()) * 2)));
        System.out.println(number.matcher("3 apples and 12 pears").replaceFirst(r -> "[" + r.group() + "]"));

        // A tiny template engine; values may contain $ and \
        Map<String, String> values = Map.of("user", "alice", "price", "$5.00", "path", "C:\\temp");
        Pattern placeholder = Pattern.compile("\\$\\{(\\w+)}");
        String template = "user=${user} price=${price} path=${path} missing=${nope}";

        try {
            placeholder.matcher(template).replaceAll(r -> values.getOrDefault(r.group(1), r.group()));
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            System.out.println("unquoted replacement -> " + e.getClass().getSimpleName());
        }
        String filled = placeholder.matcher(template)
                .replaceAll(r -> Matcher.quoteReplacement(values.getOrDefault(r.group(1), r.group())));
        System.out.println(filled);

        // appendReplacement / appendTail with a StringBuilder (Java 9): mask all but the last 4 digits
        Matcher card = Pattern.compile("\\b(\\d{12})(\\d{4})\\b").matcher("cards: 4111111111111111 and 5500000000000004");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        while (card.find()) {
            count++;
            card.appendReplacement(sb, "*".repeat(card.group(1).length()) + "$2");
        }
        card.appendTail(sb);
        System.out.println(sb + " (" + count + " masked)");

        // Pattern.quote: search for user input literally
        String userInput = "a.b*c";
        String text = "a.b*c and aXbbbc";
        System.out.println("as regex:  " + text.replaceAll(userInput, "#"));
        System.out.println("quoted:    " + text.replaceAll(Pattern.quote(userInput), "#"));
        System.out.println("quote() -> " + Pattern.quote(userInput));

        // Back-references in a plain replacement string
        System.out.println("swap: " + "Doe, John".replaceAll("(\\w+), (\\w+)", "$2 $1"));
    }
}

/* Output:
Make Every Word Title Case
6 apples and 24 pears
[3] apples and 12 pears
unquoted replacement -> IndexOutOfBoundsException
user=alice price=$5.00 path=C:\temp missing=${nope}
cards: ************1111 and ************0004 (2 masked)
as regex:  a.b*c and #
quoted:    # and aXbbbc
quote() -> \Qa.b*c\E
swap: John Doe
*/
