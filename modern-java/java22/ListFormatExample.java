// java.text.ListFormat (Java 22)
//
// Joining a list for display looks trivial until it meets real languages:
// "a, b, and c" in US English, "a, b et c" in French, "a, b und c" in
// German, "or" lists, and short forms for units. Code that hard-codes
// String.join(", ", ...) + " and " gets the conjunction, the comma before
// it and the two-element case wrong. Java 22 adds ListFormat, a Format
// backed by the Unicode CLDR list patterns: pick a locale, a Type
// (STANDARD = "and", OR, UNIT) and a Style (FULL, SHORT, NARROW). It can
// also parse a formatted list back, and accept custom patterns.
import java.text.ListFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;

public class ListFormatExample {
    public static void main(String[] args) throws ParseException {
        List<String> fruits = List.of("apples", "pears", "plums");

        // Before: hand-rolled English, wrong for two items and for any other language
        String handRolled = String.join(", ", fruits.subList(0, fruits.size() - 1)) + " and " + fruits.getLast();
        System.out.println("hand-rolled: " + handRolled);

        // After: the locale decides
        for (Locale locale : List.of(Locale.US, Locale.UK, Locale.FRANCE, Locale.GERMANY)) {
            ListFormat and = ListFormat.getInstance(locale, ListFormat.Type.STANDARD, ListFormat.Style.FULL);
            ListFormat or = ListFormat.getInstance(locale, ListFormat.Type.OR, ListFormat.Style.FULL);
            System.out.println(locale + ": " + and.format(fruits) + " | " + or.format(List.of("tea", "coffee")));
        }

        // Every size is handled, including 1 and 2
        ListFormat us = ListFormat.getInstance(Locale.US, ListFormat.Type.STANDARD, ListFormat.Style.FULL);
        for (int n = 1; n <= 4; n++) {
            System.out.println(n + " item(s): " + us.format(List.of("a", "b", "c", "d").subList(0, n)));
        }

        // UNIT lists: measurements, no conjunction in the narrow style
        System.out.println("unit narrow: " + ListFormat.getInstance(Locale.US, ListFormat.Type.UNIT, ListFormat.Style.NARROW)
                .format(List.of("5 ft", "11 in")));

        // Parsing reverses formatting
        System.out.println("parsed: " + us.parse("red, green, and blue"));

        // Custom patterns: start, middle, end, two, three
        ListFormat arrows = ListFormat.getInstance(new String[]{"{0} -> {1}", "{0} -> {1}", "{0} -> {1}", "", ""});
        System.out.println("custom: " + arrows.format(List.of("parse", "validate", "store")));
    }
}

/* Output:
hand-rolled: apples, pears and plums
en_US: apples, pears, and plums | tea or coffee
en_GB: apples, pears and plums | tea or coffee
fr_FR: apples, pears et plums | tea ou coffee
de_DE: apples, pears und plums | tea oder coffee
1 item(s): a
2 item(s): a and b
3 item(s): a, b, and c
4 item(s): a, b, c, and d
unit narrow: 5 ft 11 in
parsed: [red, green, blue]
custom: parse -> validate -> store
*/
