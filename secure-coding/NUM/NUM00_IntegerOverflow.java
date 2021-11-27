// NUM00-J. Detect or prevent integer overflow
// https://wiki.sei.cmu.edu/confluence/display/java/NUM00-J.+Detect+or+prevent+integer+overflow
//
// Java's int and long arithmetic silently wraps around on overflow (two's
// complement): Integer.MAX_VALUE + 1 is Integer.MIN_VALUE, with no exception.
// When the result feeds a security decision (a price, a quota, a buffer or
// array size, a bounds check), an attacker who controls an operand can turn
// a huge value into a small or negative one and slip past the check.
// Compliant code either checks the operands before the operation
// (precondition testing), uses the Math.*Exact methods that throw
// ArithmeticException on overflow, or widens to long / BigInteger.
import java.math.BigInteger;

public class NUM00_IntegerOverflow {
    static final int BALANCE = 100_000; // the customer's balance, in cents

    public static void main(String[] args) {
        int unitPrice = 50_000; // cents per item
        int[] quantities = {1, 2, 3, 85_900};

        for (int quantity : quantities) {
            System.out.println("quantity " + quantity
                    + " -> noncompliant: " + checkoutNoncompliant(unitPrice, quantity)
                    + " | compliant: " + checkoutCompliant(unitPrice, quantity));
        }

        // Bounds checks overflow too: offset + length wraps to a negative number
        byte[] buffer = new byte[16];
        int offset = 8, length = Integer.MAX_VALUE;
        System.out.println("noncompliant bounds check passes: " + (offset + length <= buffer.length));
        System.out.println("compliant bounds check passes:    "
                + (offset >= 0 && length >= 0 && length <= buffer.length - offset));

        // Other compliant options
        System.out.println("widening to long: " + (long) unitPrice * quantities[3]);
        System.out.println("BigInteger:       " + BigInteger.valueOf(unitPrice).multiply(BigInteger.valueOf(quantities[3])));
        try {
            Math.addExact(Integer.MAX_VALUE, 1);
        } catch (ArithmeticException e) {
            System.out.println("Math.addExact(MAX_VALUE, 1): " + e.getMessage());
        }
    }

    // Noncompliant: the total wraps around to a small number that passes the check
    static String checkoutNoncompliant(int unitPrice, int quantity) {
        int total = unitPrice * quantity;
        if (total > BALANCE) {
            return "declined";
        }
        return "charged " + total;
    }

    // Compliant: multiplyExact throws instead of wrapping
    static String checkoutCompliant(int unitPrice, int quantity) {
        try {
            int total = Math.multiplyExact(unitPrice, quantity);
            if (total > BALANCE) {
                return "declined";
            }
            return "charged " + total;
        } catch (ArithmeticException e) {
            return "rejected (overflow)";
        }
    }
}

/* Output:
quantity 1 -> noncompliant: charged 50000 | compliant: charged 50000
quantity 2 -> noncompliant: charged 100000 | compliant: charged 100000
quantity 3 -> noncompliant: declined | compliant: declined
quantity 85900 -> noncompliant: charged 32704 | compliant: rejected (overflow)
noncompliant bounds check passes: true
compliant bounds check passes:    false
widening to long: 4295000000
BigInteger:       4295000000
Math.addExact(MAX_VALUE, 1): integer overflow
*/
