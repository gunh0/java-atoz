// Effective Java, Item 70: Use checked exceptions for recoverable conditions
// and runtime exceptions for programming errors (recovery information).
//
// A checked exception tells the caller "this can happen and you can recover".
// Recovery needs data, so give the exception accessor methods that return it
// (how much money is missing, which field was invalid) instead of making
// callers parse the message string. Two more rules from the item: do not
// subclass Error - errors are reserved for the JVM (OutOfMemoryError,
// StackOverflowError) - and never throw a bare Throwable, which neither the
// compiler nor callers can reason about. Unchecked exceptions you write
// should extend RuntimeException.
public class RecoveryInformation {
    public static void main(String[] args) {
        Account account = new Account(120);

        // Recoverable: the caller reads the shortfall and tries again
        long amount = 200;
        try {
            account.withdraw(amount);
        } catch (InsufficientFundsException e) {
            System.out.println("caught: " + e.getMessage());
            System.out.println("recover: top up " + e.shortfall() + " and retry");
            account.deposit(e.shortfall());
            try {
                account.withdraw(amount);
                System.out.println("retry ok, balance = " + account.balance());
            } catch (InsufficientFundsException again) {
                System.out.println("still failing");
            }
        }

        // Programming error: a negative amount is a bug in the caller
        try {
            account.withdraw(-5);
        } catch (IllegalArgumentException | InsufficientFundsException e) {
            System.out.println("bug: " + e);
        }

        // Anti-patterns: a custom Error and a bare Throwable
        try {
            legacyWithdraw();
        } catch (Throwable t) { // forced to catch everything, including real JVM errors
            System.out.println("legacy threw " + t.getClass().getSimpleName()
                    + ", is Error: " + (t instanceof Error) + ", is Exception: " + (t instanceof Exception));
        }
    }

    // Don't: misuses Error (callers may treat it as a fatal JVM failure)
    static class AccountError extends Error {
        AccountError(String message) {
            super(message);
        }
    }

    static void legacyWithdraw() throws Throwable {
        throw new AccountError("overdrawn"); // a `throws Throwable` signature says nothing
    }
}

class InsufficientFundsException extends Exception {
    private final long shortfall;

    InsufficientFundsException(long balance, long requested) {
        super("requested " + requested + " but balance is " + balance);
        this.shortfall = requested - balance;
    }

    // Recovery information, available without parsing the message
    long shortfall() {
        return shortfall;
    }
}

class Account {
    private long balance;

    Account(long balance) {
        this.balance = balance;
    }

    void withdraw(long amount) throws InsufficientFundsException {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
        if (amount > balance) {
            throw new InsufficientFundsException(balance, amount);
        }
        balance -= amount;
    }

    void deposit(long amount) {
        balance += amount;
    }

    long balance() {
        return balance;
    }
}

/* Output:
caught: requested 200 but balance is 120
recover: top up 80 and retry
retry ok, balance = 0
bug: java.lang.IllegalArgumentException: amount must be positive: -5
legacy threw AccountError, is Error: true, is Exception: false
*/
