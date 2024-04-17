// DecimalFormat is the concrete NumberFormat behind most locales, driven by a
// pattern:
//   0  a digit, always shown (pads with zeros)     #  a digit, omitted if zero
//   ,  grouping separator                          .  decimal separator
//   %  multiply by 100 and show a percent sign     E  scientific notation
//   ;  separates the positive and negative subpattern
//   'x' quotes literal text
// The symbols actually printed come from DecimalFormatSymbols, so build the
// format with explicit symbols when the output must not depend on the machine.
// setParseBigDecimal(true) makes parse() return an exact BigDecimal.
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.Locale;

public class DecimalFormatExample {
    static final DecimalFormatSymbols ROOT = DecimalFormatSymbols.getInstance(Locale.ROOT);

    static void show(String pattern, double... values) {
        DecimalFormat df = new DecimalFormat(pattern, ROOT);
        StringBuilder sb = new StringBuilder(String.format("%-24s", pattern));
        for (double v : values) {
            sb.append(" | ").append(df.format(v));
        }
        System.out.println(sb);
    }

    public static void main(String[] args) throws ParseException {
        show("0.00", 3.14159, 0.5, 1234.5);
        show("#.##", 3.14159, 0.5, 1234.5);
        show("000.###", 3.14159, 0.5, 1234.5);
        show("#,##0.00", 3.14159, 0.5, 1234567.5);
        show("0.0%", 0.256, 0.5, 1.0);
        show("0.###E0", 123456, 0.000123, 1);
        show("00.00E0", 123456, 0.000123, 1);
        show("#,##0.00;(#,##0.00)", 1234.5, -1234.5, 0);
        show("'#'000", 7, 42, 1234);
        show("0.00 'USD'", 9.99, 0, 100);

        // Rounding mode (default HALF_EVEN)
        DecimalFormat money = new DecimalFormat("0.00", ROOT);
        System.out.println("HALF_EVEN 2.345 -> " + money.format(new BigDecimal("2.345")));
        money.setRoundingMode(RoundingMode.HALF_UP);
        System.out.println("HALF_UP   2.345 -> " + money.format(new BigDecimal("2.345")));
        money.setRoundingMode(RoundingMode.DOWN);
        System.out.println("DOWN      2.349 -> " + money.format(new BigDecimal("2.349")));

        // Custom symbols: Swiss-style grouping with an apostrophe
        DecimalFormatSymbols swiss = new DecimalFormatSymbols(Locale.ROOT);
        swiss.setGroupingSeparator('\'');
        swiss.setDecimalSeparator('.');
        System.out.println("custom symbols: " + new DecimalFormat("#,##0.00", swiss).format(1234567.891));

        // Grouping size comes from the pattern: groups of 4
        System.out.println("grouping of 4: " + new DecimalFormat("#,####", ROOT).format(123456789));

        // Parsing: Double by default (loses precision), BigDecimal on request
        String big = "12345678901234567890.123";
        DecimalFormat parser = new DecimalFormat("#,##0.###", ROOT);
        Number asDouble = parser.parse(big);
        System.out.println("parse -> " + asDouble.getClass().getSimpleName() + " " + asDouble);
        parser.setParseBigDecimal(true);
        Number exact = parser.parse(big);
        System.out.println("parse -> " + exact.getClass().getSimpleName() + " " + exact);

        // toPattern() shows the effective pattern
        DecimalFormat applied = new DecimalFormat("", ROOT);
        applied.applyPattern("#,##0.0#");
        System.out.println("toPattern: " + applied.toPattern() + ", min/max fraction: "
                + applied.getMinimumFractionDigits() + "/" + applied.getMaximumFractionDigits());
    }
}

/* Output:
0.00                     | 3.14 | 0.50 | 1234.50
#.##                     | 3.14 | 0.5 | 1234.5
000.###                  | 003.142 | 000.5 | 1234.5
#,##0.00                 | 3.14 | 0.50 | 1,234,567.50
0.0%                     | 25.6% | 50.0% | 100.0%
0.###E0                  | 1.235E5 | 1.23E-4 | 1E0
00.00E0                  | 12.35E4 | 12.30E-5 | 10.00E-1
#,##0.00;(#,##0.00)      | 1,234.50 | (1,234.50) | 0.00
'#'000                   | #007 | #042 | #1234
0.00 'USD'               | 9.99 USD | 0.00 USD | 100.00 USD
HALF_EVEN 2.345 -> 2.34
HALF_UP   2.345 -> 2.35
DOWN      2.349 -> 2.34
custom symbols: 1'234'567.89
grouping of 4: 1,2345,6789
parse -> Double 1.2345678901234567E19
parse -> BigDecimal 12345678901234567890.123
toPattern: #,##0.0#, min/max fraction: 1/2
*/
