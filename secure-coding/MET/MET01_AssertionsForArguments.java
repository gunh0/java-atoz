// MET01-J. Never use assertions to validate method arguments
// https://wiki.sei.cmu.edu/confluence/display/java/MET01-J.+Never+use+assertions+to+validate+method+arguments
//
// `assert` statements are disabled by default and are switched on only
// with -ea (or -enableassertions). They exist to document and test
// internal invariants during development, not to defend a public API: in
// a normal production JVM an argument check written as an assertion is
// simply not executed, so the invalid value flows into the object. Check
// arguments of public methods explicitly and throw a meaningful exception
// (IllegalArgumentException, NullPointerException via
// Objects.requireNonNull, IndexOutOfBoundsException via Objects.checkIndex).
import java.util.Objects;

public class MET01_AssertionsForArguments {
    public static void main(String[] args) {
        System.out.println("assertions enabled for this program: "
                + MET01_AssertionsForArguments.class.desiredAssertionStatus());

        AccountNoncompliant bad = new AccountNoncompliant();
        bad.withdraw(-500); // a negative withdrawal is a deposit
        System.out.println("noncompliant: balance after withdraw(-500) = " + bad.balance());

        AccountCompliant good = new AccountCompliant();
        try {
            good.withdraw(-500);
        } catch (IllegalArgumentException e) {
            System.out.println("compliant:    withdraw(-500) rejected: " + e.getMessage());
        }
        System.out.println("compliant:    balance = " + good.balance());

        try {
            good.rename(null);
        } catch (NullPointerException e) {
            System.out.println("compliant:    rename(null) rejected: " + e.getMessage());
        }
    }
}

class AccountNoncompliant {
    private long balance = 100;

    void withdraw(long amount) {
        assert amount > 0 : "amount must be positive"; // skipped without -ea
        assert amount <= balance : "insufficient funds";
        balance -= amount;
    }

    long balance() {
        return balance;
    }
}

class AccountCompliant {
    private long balance = 100;
    private String name = "main";

    void withdraw(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (amount > balance) {
            throw new IllegalArgumentException("insufficient funds");
        }
        balance -= amount;
        assert balance >= 0; // an internal invariant: a fine use of assert
    }

    void rename(String newName) {
        name = Objects.requireNonNull(newName, "name");
    }

    long balance() {
        return balance;
    }
}

/* Output:
assertions enabled for this program: false
noncompliant: balance after withdraw(-500) = 600
compliant:    withdraw(-500) rejected: amount must be positive
compliant:    balance = 100
compliant:    rename(null) rejected: name
*/
