// NUM07-J. Do not attempt comparisons with NaN
// https://wiki.sei.cmu.edu/confluence/display/java/NUM07-J.+Do+not+attempt+comparisons+with+NaN
//
// NaN (not a number) is unordered: every comparison involving NaN is false,
// including NaN == NaN, and NaN != x is always true. So x == Double.NaN can
// never detect a NaN, and range checks written as "reject if out of range"
// (if (x < min || x > max) reject) let NaN straight through because both
// halves are false. Use Double.isNaN() (or Double.isFinite() to also exclude
// infinities), or write range checks in the positive form
// (if (x >= min && x <= max) accept), which NaN fails.
public class NUM07_NaNComparison {
    public static void main(String[] args) {
        double x = Double.parseDouble("NaN"); // e.g. a "discount" field from a request
        System.out.println("x == Double.NaN: " + (x == Double.NaN) + ", x != x: " + (x != x) + ", Double.isNaN(x): " + Double.isNaN(x));

        for (String input : new String[] {"0.15", "1.5", "NaN"}) {
            double discount = Double.parseDouble(input);
            System.out.println("discount " + input + ": noncompliant " + applyNoncompliant(100.0, discount)
                    + " | compliant " + applyCompliant(100.0, discount));
        }
    }

    // Noncompliant: NaN is neither < 0 nor > 0.5, so it is accepted
    static String applyNoncompliant(double price, double discount) {
        if (discount < 0.0 || discount > 0.5 || discount == Double.NaN) {
            return "rejected";
        }
        return "charged " + price * (1 - discount);
    }

    // Compliant: NaN (and infinities) are rejected explicitly
    static String applyCompliant(double price, double discount) {
        if (!Double.isFinite(discount) || discount < 0.0 || discount > 0.5) {
            return "rejected";
        }
        return "charged " + price * (1 - discount);
    }
}

/* Output:
x == Double.NaN: false, x != x: true, Double.isNaN(x): true
discount 0.15: noncompliant charged 85.0 | compliant charged 85.0
discount 1.5: noncompliant rejected | compliant rejected
discount NaN: noncompliant charged NaN | compliant rejected
*/
