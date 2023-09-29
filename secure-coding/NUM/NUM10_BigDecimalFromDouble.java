// NUM10-J. Do not construct BigDecimal objects from floating-point literals
// https://wiki.sei.cmu.edu/confluence/display/java/NUM10-J.+Do+not+construct+BigDecimal+objects+from+floating-point+literals
//
// BigDecimal exists to represent decimal amounts exactly, but
// new BigDecimal(0.1) is handed a double, and the double nearest to 0.1 is
// 0.1000000000000000055511151231257827021181583404541015625. The constructor
// faithfully preserves that error, so equality checks against "0.1" fail
// and rounded totals can drift by a cent. Construct from a String
// (new BigDecimal("0.1")), or use BigDecimal.valueOf(double), which goes
// through Double.toString() and yields the short decimal form.
import java.math.BigDecimal;
import java.math.RoundingMode;

public class NUM10_BigDecimalFromDouble {
    public static void main(String[] args) {
        System.out.println("new BigDecimal(0.1)     = " + new BigDecimal(0.1));
        System.out.println("new BigDecimal(\"0.1\")   = " + new BigDecimal("0.1"));
        System.out.println("BigDecimal.valueOf(0.1) = " + BigDecimal.valueOf(0.1));

        // A fee of 1.005 rounded half-up to cents should be 1.01
        System.out.println("noncompliant fee: " + new BigDecimal(1.005).setScale(2, RoundingMode.HALF_UP));
        System.out.println("compliant fee:    " + new BigDecimal("1.005").setScale(2, RoundingMode.HALF_UP));

        // A coupon check compares with the expected rate
        BigDecimal expected = new BigDecimal("0.1");
        System.out.println("noncompliant rate matches: " + (new BigDecimal(0.1).compareTo(expected) == 0));
        System.out.println("compliant rate matches:    " + (BigDecimal.valueOf(0.1).compareTo(expected) == 0));
    }
}

/* Output:
new BigDecimal(0.1)     = 0.1000000000000000055511151231257827021181583404541015625
new BigDecimal("0.1")   = 0.1
BigDecimal.valueOf(0.1) = 0.1
noncompliant fee: 1.00
compliant fee:    1.01
noncompliant rate matches: false
compliant rate matches:    true
*/
