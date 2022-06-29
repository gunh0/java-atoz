// OBJ01-J. Limit accessibility of fields
// https://wiki.sei.cmu.edu/confluence/display/java/OBJ01-J.+Limit+accessibility+of+fields
//
// A public (or protected) field can be read and written by any code that
// can see the class, so the class can no longer enforce its invariants,
// validate values, or notice changes. For security-relevant state this is
// fatal: untrusted code can raise its own limits, switch off checks, or
// replace a reference that later security decisions rely on. Declare fields
// private, expose state through accessor methods that validate and copy,
// and make constants static final (and immutable).
public class OBJ01_LimitFieldAccessibility {
    public static void main(String[] args) {
        // Noncompliant: "untrusted" code edits the fields directly
        LoginPolicyNoncompliant.maxAttempts = Integer.MAX_VALUE;
        AccountNoncompliant victim = new AccountNoncompliant(100);
        victim.balance = -1_000_000;
        System.out.println("noncompliant: max attempts = " + LoginPolicyNoncompliant.maxAttempts
                + ", balance = " + victim.balance);
        System.out.println("noncompliant: 50th attempt allowed = " + LoginPolicyNoncompliant.allowAttempt(50));

        // Compliant: no fields to touch, accessors enforce the rules
        AccountCompliant account = new AccountCompliant(100);
        try {
            account.withdraw(1_000_000);
        } catch (IllegalArgumentException e) {
            System.out.println("compliant: withdraw rejected (" + e.getMessage() + "), balance = " + account.balance());
        }
        System.out.println("compliant: max attempts = " + LoginPolicyCompliant.maxAttempts()
                + ", 50th attempt allowed = " + LoginPolicyCompliant.allowAttempt(50));
    }
}

class LoginPolicyNoncompliant {
    public static int maxAttempts = 5; // public and mutable: anyone can raise it

    static boolean allowAttempt(int attempt) {
        return attempt <= maxAttempts;
    }
}

class AccountNoncompliant {
    public int balance; // no way to keep balance >= 0

    AccountNoncompliant(int balance) {
        this.balance = balance;
    }
}

final class LoginPolicyCompliant {
    private static final int MAX_ATTEMPTS = 5;

    private LoginPolicyCompliant() {
    }

    static int maxAttempts() {
        return MAX_ATTEMPTS;
    }

    static boolean allowAttempt(int attempt) {
        return attempt <= MAX_ATTEMPTS;
    }
}

final class AccountCompliant {
    private int balance;

    AccountCompliant(int balance) {
        if (balance < 0) {
            throw new IllegalArgumentException("negative opening balance");
        }
        this.balance = balance;
    }

    int balance() {
        return balance;
    }

    void withdraw(int amount) {
        if (amount <= 0 || amount > balance) {
            throw new IllegalArgumentException("invalid amount " + amount);
        }
        balance -= amount;
    }
}

/* Output:
noncompliant: max attempts = 2147483647, balance = -1000000
noncompliant: 50th attempt allowed = true
compliant: withdraw rejected (invalid amount 1000000), balance = 100
compliant: max attempts = 5, 50th attempt allowed = false
*/
