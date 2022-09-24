// Java 12 — CompactNumberFormat (API addition, no JEP)
//
// Dashboards and social apps show "1.2K followers" or "3 million views"
// instead of full numbers. Before Java 12 every project wrote its own
// thresholds-and-suffixes helper, usually English-only and with rounding
// bugs. NumberFormat.getCompactNumberInstance(locale, style) formats (and
// parses) numbers in the short or long compact form defined by the Unicode
// CLDR data for that locale, so "1K" in English becomes "1천" in Korean and
// the units follow the local convention (Korean groups by 10,000).
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

public class CompactNumberFormatting {
    public static void main(String[] args) throws ParseException {
        long[] values = {999, 1_000, 1_234, 56_789, 1_500_000, 2_000_000_000L};

        // Before: a hand-rolled helper, English only, truncating
        for (long v : values) {
            System.out.printf("%,15d  homemade=%-6s", v, homemade(v));
            NumberFormat shortUs = NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT);
            NumberFormat longUs = NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.LONG);
            System.out.printf("  SHORT=%-5s  LONG=%s%n", shortUs.format(v), longUs.format(v));
        }

        // Fraction digits and rounding are configurable
        NumberFormat precise = NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT);
        precise.setMaximumFractionDigits(1);
        precise.setRoundingMode(RoundingMode.DOWN);
        System.out.println("1,234 with one fraction digit: " + precise.format(1_234));
        System.out.println("1,567,000 rounded down:        " + precise.format(1_567_000));

        // Parsing goes the other way
        NumberFormat parser = NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT);
        System.out.println("parse(\"5K\") = " + parser.parse("5K"));
        System.out.println("parse(\"2M\") = " + parser.parse("2M"));
    }

    static String homemade(long v) {
        if (v >= 1_000_000_000) return v / 1_000_000_000 + "B";
        if (v >= 1_000_000) return v / 1_000_000 + "M";
        if (v >= 1_000) return v / 1_000 + "K";
        return Long.toString(v);
    }
}

/* Output:
            999  homemade=999     SHORT=999    LONG=999
          1,000  homemade=1K      SHORT=1K     LONG=1 thousand
          1,234  homemade=1K      SHORT=1K     LONG=1 thousand
         56,789  homemade=56K     SHORT=57K    LONG=57 thousand
      1,500,000  homemade=1M      SHORT=2M     LONG=2 million
  2,000,000,000  homemade=2B      SHORT=2B     LONG=2 billion
1,234 with one fraction digit: 1.2K
1,567,000 rounded down:        1.5M
parse("5K") = 5000
parse("2M") = 2000000
*/
