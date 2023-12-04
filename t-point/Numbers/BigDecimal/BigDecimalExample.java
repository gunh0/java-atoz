// BigDecimal is an exact decimal number: an arbitrary-precision unscaled
// integer plus a scale (digits after the point). Use it for money and any
// value that must round the way people expect; double is binary and cannot
// represent 0.1 exactly.
//  - construct from a String (or BigDecimal.valueOf(double)), never
//    new BigDecimal(0.1), which captures the binary approximation
//  - it is immutable: add/multiply/... return new objects
//  - divide() throws if the result has no finite decimal expansion unless
//    you give a scale and RoundingMode (or a MathContext)
//  - equals() compares value AND scale (2.0 != 2.00); use compareTo()
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

public class BigDecimalExample {
    public static void main(String[] args) {
        System.out.println("double 0.1 + 0.2       = " + (0.1 + 0.2));
        System.out.println("BigDecimal 0.1 + 0.2   = " + new BigDecimal("0.1").add(new BigDecimal("0.2")));
        System.out.println("new BigDecimal(0.1)    = " + new BigDecimal(0.1));
        System.out.println("BigDecimal.valueOf(0.1) = " + BigDecimal.valueOf(0.1));

        // An invoice: unit price x quantity, then 10% tax rounded to cents
        List<String[]> lines = List.of(
                new String[] {"vpn seat", "4.99", "3"},
                new String[] {"waf rule pack", "19.95", "1"},
                new String[] {"log storage GB", "0.023", "350"});
        BigDecimal subtotal = BigDecimal.ZERO;
        for (String[] line : lines) {
            BigDecimal amount = new BigDecimal(line[1]).multiply(new BigDecimal(line[2]));
            System.out.printf("  %-15s %7s x %3s = %s%n", line[0], line[1], line[2], amount);
            subtotal = subtotal.add(amount);
        }
        BigDecimal tax = subtotal.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.setScale(2, RoundingMode.HALF_UP).add(tax);
        System.out.println("  subtotal " + subtotal + ", tax " + tax + ", total " + total);

        // Rounding modes on the classic halfway cases
        for (String v : new String[] {"2.345", "2.355", "-2.345"}) {
            BigDecimal d = new BigDecimal(v);
            System.out.printf("  %-6s HALF_UP=%s HALF_EVEN=%s DOWN=%s CEILING=%s%n", v,
                    d.setScale(2, RoundingMode.HALF_UP), d.setScale(2, RoundingMode.HALF_EVEN),
                    d.setScale(2, RoundingMode.DOWN), d.setScale(2, RoundingMode.CEILING));
        }

        // Division needs a rounding rule when the result does not terminate
        BigDecimal hundred = new BigDecimal("100");
        try {
            hundred.divide(new BigDecimal("3"));
        } catch (ArithmeticException e) {
            System.out.println("100 / 3 -> ArithmeticException: " + e.getMessage());
        }
        System.out.println("100 / 3 (scale 2, HALF_EVEN) = " + hundred.divide(new BigDecimal("3"), 2, RoundingMode.HALF_EVEN));
        System.out.println("100 / 3 (MathContext.DECIMAL32) = " + hundred.divide(new BigDecimal("3"), MathContext.DECIMAL32));

        // equals vs compareTo
        BigDecimal a = new BigDecimal("2.0"), b = new BigDecimal("2.00");
        System.out.println("2.0 equals 2.00: " + a.equals(b) + ", compareTo: " + a.compareTo(b)
                + ", stripTrailingZeros equal: " + a.stripTrailingZeros().equals(b.stripTrailingZeros()));
        System.out.println("scale/precision of 123.4500: " + new BigDecimal("123.4500").scale()
                + "/" + new BigDecimal("123.4500").precision()
                + ", toPlainString of 1E+3: " + new BigDecimal("1E+3").toPlainString());
    }
}

/* Output:
double 0.1 + 0.2       = 0.30000000000000004
BigDecimal 0.1 + 0.2   = 0.3
new BigDecimal(0.1)    = 0.1000000000000000055511151231257827021181583404541015625
BigDecimal.valueOf(0.1) = 0.1
  vpn seat           4.99 x   3 = 14.97
  waf rule pack     19.95 x   1 = 19.95
  log storage GB    0.023 x 350 = 8.050
  subtotal 42.970, tax 4.30, total 47.27
  2.345  HALF_UP=2.35 HALF_EVEN=2.34 DOWN=2.34 CEILING=2.35
  2.355  HALF_UP=2.36 HALF_EVEN=2.36 DOWN=2.35 CEILING=2.36
  -2.345 HALF_UP=-2.35 HALF_EVEN=-2.34 DOWN=-2.34 CEILING=-2.34
100 / 3 -> ArithmeticException: Non-terminating decimal expansion; no exact representable decimal result.
100 / 3 (scale 2, HALF_EVEN) = 33.33
100 / 3 (MathContext.DECIMAL32) = 33.33333
2.0 equals 2.00: false, compareTo: 0, stripTrailingZeros equal: true
scale/precision of 123.4500: 4/7, toPlainString of 1E+3: 1000
*/
