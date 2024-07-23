// NUM08-J. Check floating-point inputs for exceptional values
// https://wiki.sei.cmu.edu/confluence/display/java/NUM08-J.+Check+floating-point+inputs+for+exceptional+values
//
// Double.parseDouble accepts "NaN", "Infinity" and "-Infinity" as well as
// numbers too large to represent (which become Infinity). These values
// slip past range checks: every comparison with NaN is false, so a test
// like `if (amount < 0 || amount > limit) reject` lets NaN through, and
// NaN then poisons every computation it touches (a NaN balance is neither
// above nor below any threshold). Validate untrusted floating-point input
// with Double.isFinite (or isNaN/isInfinite) before any range check, or
// avoid binary floating point for such values altogether.
public class NUM08_FloatingPointInputs {
    static final double LIMIT = 10_000;

    public static void main(String[] args) {
        String[] inputs = {"250.00", "-5", "NaN", "Infinity", "1e400", "0x1p3"};

        for (String input : inputs) {
            double amount = Double.parseDouble(input);
            System.out.printf("%-8s -> %-9s noncompliant: %-8s compliant: %s%n",
                    input, amount, checkNoncompliant(amount), checkCompliant(amount));
        }

        // What happens downstream when NaN gets in
        double balance = 1_000;
        balance -= Double.parseDouble("NaN");
        System.out.println("balance after a NaN withdrawal: " + balance
                + ", balance < 0: " + (balance < 0) + ", balance >= 0: " + (balance >= 0));
    }

    // Noncompliant: range check only
    static String checkNoncompliant(double amount) {
        if (amount < 0 || amount > LIMIT) {
            return "rejected";
        }
        return "accepted";
    }

    // Compliant: reject NaN and infinities first
    static String checkCompliant(double amount) {
        if (!Double.isFinite(amount)) {
            return "rejected";
        }
        if (amount < 0 || amount > LIMIT) {
            return "rejected";
        }
        return "accepted";
    }
}

/* Output:
250.00   -> 250.0     noncompliant: accepted compliant: accepted
-5       -> -5.0      noncompliant: rejected compliant: rejected
NaN      -> NaN       noncompliant: accepted compliant: rejected
Infinity -> Infinity  noncompliant: rejected compliant: rejected
1e400    -> Infinity  noncompliant: rejected compliant: rejected
0x1p3    -> 8.0       noncompliant: accepted compliant: accepted
balance after a NaN withdrawal: NaN, balance < 0: false, balance >= 0: false
*/
