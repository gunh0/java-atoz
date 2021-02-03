public class BankAccount {
    private double balance;

    public BankAccount(double initialBalance) {
        this.balance = initialBalance;
    }

    /**
     * Withdraws the given amount from the account.
     *
     * @param amount The amount to withdraw
     * @throws InsufficientFundsException if the account does not have enough
     *                                    balance
     */
    public void withdraw(double amount) throws InsufficientFundsException {
        if (amount > balance) {
            throw new InsufficientFundsException("Insufficient funds. You have only: " + balance);
        }
        balance -= amount;
    }

    public double getBalance() {
        return balance;
    }

    public static void main(String[] args) {
        BankAccount account = new BankAccount(100.0);

        try {
            account.withdraw(150.0);
        } catch (InsufficientFundsException e) {
            System.out.println(e.getMessage());
            // Here, the caller can recover, maybe by prompting the user for a different
            // amount
        }
    }
}

class InsufficientFundsException extends Exception {
    public InsufficientFundsException(String message) {
        super(message);
    }
}
