// NUM11-J. Do not compare or inspect the string representation of floating-point values
// https://wiki.sei.cmu.edu/confluence/display/java/NUM11-J.+Do+not+compare+or+inspect+the+string+representation+of+floating-point+values
//
// Double.toString is meant for humans. It switches to scientific notation
// outside 10^-3 .. 10^7 ("1.0E-5", "1.0E7"), prints the shortest digits that
// identify the binary value (so 0.1 + 0.2 is "0.30000000000000004"), widens
// a float into many more digits ("0.10000000149011612"), and its exact
// output has even changed between Java releases. Code that validates a
// value by looking for "." or counting characters after it, or that
// compares toString() results, accepts and rejects the wrong values.
// Compare numbers as numbers (with an explicit tolerance where rounding is
// expected), test integrality with arithmetic, and validate decimal places
// on the ORIGINAL decimal text with BigDecimal, before it becomes a double.
import java.math.BigDecimal;
import java.util.List;

public class NUM11_FloatStringComparison {
    // Noncompliant: "is it a whole number?" by looking at the string
    static boolean isWholeNoncompliant(double d) {
        return String.valueOf(d).endsWith(".0");
    }

    // Compliant: arithmetic test
    static boolean isWholeCompliant(double d) {
        return Double.isFinite(d) && d == Math.rint(d);
    }

    // Noncompliant: "at most two decimal places" (an amount in cents) by counting characters
    static boolean twoDecimalsNoncompliant(double amount) {
        String s = Double.toString(amount);
        return s.length() - s.indexOf('.') - 1 <= 2;
    }

    // Compliant: validate the decimal text the user sent, then convert
    static boolean twoDecimalsCompliant(String amount) {
        try {
            return new BigDecimal(amount).stripTrailingZeros().scale() <= 2;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static void main(String[] args) {
        System.out.println("whole-number check   noncompliant  compliant");
        for (double d : new double[] {42.0, 1e7, 1e20, 2.5}) {
            System.out.printf("  %-18s %-13s %s%n", Double.toString(d), isWholeNoncompliant(d), isWholeCompliant(d));
        }

        System.out.println("two-decimals check       noncompliant  compliant");
        for (String input : List.of("19.99", "0.00001", "12345678.5", "0.1", "19.990000000000000001")) {
            double parsed = Double.parseDouble(input);
            System.out.printf("  %-22s %-13s %s%n", input, twoDecimalsNoncompliant(parsed), twoDecimalsCompliant(input));
        }

        // Equality through strings
        double sum = 0.1 + 0.2;
        System.out.println("toString(0.1 + 0.2).equals(\"0.3\"): " + Double.toString(sum).equals("0.3")
                + ", |0.1 + 0.2 - 0.3| < 1e-9: " + (Math.abs(sum - 0.3) < 1e-9));
        float f = 0.1f;
        System.out.println("Float.toString(0.1f) = " + Float.toString(f) + ", Double.toString(0.1f) = "
                + Double.toString(f) + ", strings equal: " + Float.toString(f).equals(Double.toString(f))
                + ", values equal: " + (f == (double) f));
    }
}

/* Output:
whole-number check   noncompliant  compliant
  42.0               true          true
  1.0E7              false         true
  1.0E20             false         true
  2.5                false         false
two-decimals check       noncompliant  compliant
  19.99                  true          true
  0.00001                false         false
  12345678.5             false         true
  0.1                    true          true
  19.990000000000000001  true          false
toString(0.1 + 0.2).equals("0.3"): false, |0.1 + 0.2 - 0.3| < 1e-9: true
Float.toString(0.1f) = 0.1, Double.toString(0.1f) = 0.10000000149011612, strings equal: false, values equal: true
*/
