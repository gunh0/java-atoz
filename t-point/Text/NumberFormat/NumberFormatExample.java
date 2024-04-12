// java.text.NumberFormat formats and parses numbers the way a locale expects:
// grouping and decimal separators, currency symbols, percentages and (since
// Java 12) compact forms like "1.2K". Get one from a factory method:
// getInstance / getNumberInstance, getIntegerInstance, getCurrencyInstance,
// getPercentInstance, getCompactNumberInstance.
//  - Always pass a Locale: the no-arg versions use the machine's default.
//  - Rounding is HALF_EVEN by default; change it with setRoundingMode.
//  - parse() stops at the first character it cannot use and does not fail,
//    so check the ParsePosition when trailing garbage must be rejected.
//  - NumberFormat instances are not thread-safe: create one per use or thread.
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Locale;

public class NumberFormatExample {
    // Some locales use non-breaking spaces; escape them so the output is plain ASCII
    static String ascii(String s) {
        StringBuilder sb = new StringBuilder();
        s.chars().forEach(c -> sb.append(c < 128 ? String.valueOf((char) c) : String.format("\\u%04x", c)));
        return sb.toString();
    }

    public static void main(String[] args) throws ParseException {
        double amount = 1234567.891;

        System.out.println("number   US: " + NumberFormat.getInstance(Locale.US).format(amount));
        System.out.println("number   DE: " + NumberFormat.getInstance(Locale.GERMANY).format(amount));
        System.out.println("integer  US: " + NumberFormat.getIntegerInstance(Locale.US).format(2.5)
                + " and " + NumberFormat.getIntegerInstance(Locale.US).format(3.5) + " (HALF_EVEN)");
        System.out.println("currency US: " + NumberFormat.getCurrencyInstance(Locale.US).format(amount));
        System.out.println("currency DE: " + ascii(NumberFormat.getCurrencyInstance(Locale.GERMANY).format(amount)));
        System.out.println("percent  US: " + NumberFormat.getPercentInstance(Locale.US).format(0.256));

        // Compact number formatting (Java 12)
        NumberFormat shortForm = NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT);
        NumberFormat longForm = NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.LONG);
        shortForm.setMaximumFractionDigits(1);
        for (long n : new long[] {999, 1_200, 45_678, 3_400_000}) {
            System.out.println("compact " + n + ": " + shortForm.format(n) + " / " + longForm.format(n));
        }

        // Fraction digits and rounding mode
        NumberFormat two = NumberFormat.getInstance(Locale.US);
        two.setMinimumFractionDigits(2);
        two.setMaximumFractionDigits(2);
        System.out.println("HALF_EVEN 0.125 -> " + two.format(0.125) + ", 0.375 -> " + two.format(0.375));
        two.setRoundingMode(RoundingMode.HALF_UP);
        System.out.println("HALF_UP   0.125 -> " + two.format(0.125) + ", 0.375 -> " + two.format(0.375));
        two.setGroupingUsed(false);
        System.out.println("no grouping: " + two.format(amount));

        // Parsing is locale-sensitive too
        System.out.println("parse DE \"1.234,5\" -> " + NumberFormat.getInstance(Locale.GERMANY).parse("1.234,5"));
        System.out.println("parse US \"1.234,5\" -> " + NumberFormat.getInstance(Locale.US).parse("1.234,5"));

        // parse() silently ignores trailing text; use a ParsePosition to notice it
        NumberFormat us = NumberFormat.getInstance(Locale.US);
        String input = "42abc";
        System.out.println("parse \"" + input + "\" -> " + us.parse(input));
        ParsePosition pos = new ParsePosition(0);
        Number n = us.parse(input, pos);
        boolean wholeInput = pos.getIndex() == input.length();
        System.out.println("strict parse \"" + input + "\" -> " + (wholeInput ? n : "rejected, stopped at index " + pos.getIndex()));

        try {
            us.parse("abc");
        } catch (ParseException e) {
            System.out.println("parse \"abc\" -> ParseException at offset " + e.getErrorOffset());
        }
    }
}

/* Output:
number   US: 1,234,567.891
number   DE: 1.234.567,891
integer  US: 2 and 4 (HALF_EVEN)
currency US: $1,234,567.89
currency DE: 1.234.567,89\u00a0\u20ac
percent  US: 26%
compact 999: 999 / 999
compact 1200: 1.2K / 1 thousand
compact 45678: 45.7K / 46 thousand
compact 3400000: 3.4M / 3 million
HALF_EVEN 0.125 -> 0.12, 0.375 -> 0.38
HALF_UP   0.125 -> 0.13, 0.375 -> 0.38
no grouping: 1234567.89
parse DE "1.234,5" -> 1234.5
parse US "1.234,5" -> 1.234
parse "42abc" -> 42
strict parse "42abc" -> rejected, stopped at index 2
parse "abc" -> ParseException at offset 0
*/
