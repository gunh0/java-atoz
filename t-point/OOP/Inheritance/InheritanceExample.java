// Inheritance lets a class (subclass) reuse and extend another (superclass)
// with `extends`. It models an IS-A relationship: a SavingsAccount is an
// Account.
//  - the subclass inherits accessible fields and methods and can add its own
//  - Java has single inheritance for classes (one direct superclass), but
//    chains are allowed: CheckingAccount -> Account -> Object
//  - every class ultimately extends java.lang.Object
//  - constructors are NOT inherited; the subclass constructor runs the
//    superclass constructor first
// Prefer inheritance only when IS-A truly holds; otherwise use composition.
public class InheritanceExample {
    public static void main(String[] args) {
        SavingsAccount savings = new SavingsAccount("Kim", 1000, 3);
        savings.deposit(500);          // inherited from Account
        savings.addMonthlyInterest();  // declared in SavingsAccount
        System.out.println(savings.statement());

        CheckingAccount checking = new CheckingAccount("Lee", 100, 300);
        System.out.println("withdraw 350: " + checking.withdraw(350)); // inherited, uses overdraft hook
        System.out.println("withdraw 100: " + checking.withdraw(100));
        System.out.println(checking.statement());

        // IS-A checks walk up the hierarchy
        System.out.println("savings instanceof Account: " + (savings instanceof Account));
        System.out.println("superclass of SavingsAccount: " + SavingsAccount.class.getSuperclass().getSimpleName());
        System.out.println("superclass of Account: " + Account.class.getSuperclass().getSimpleName());
    }
}

class Account {
    protected final String owner;
    protected long balance;

    Account(String owner, long openingBalance) {
        System.out.println("Account constructor for " + owner);
        this.owner = owner;
        this.balance = openingBalance;
    }

    void deposit(long amount) {
        balance += amount;
    }

    boolean withdraw(long amount) {
        if (amount > balance + overdraftLimit()) {
            return false;
        }
        balance -= amount;
        return true;
    }

    // A hook subclasses can change; a plain account allows no overdraft
    long overdraftLimit() {
        return 0;
    }

    String statement() {
        return getClass().getSimpleName() + "(" + owner + ") balance=" + balance;
    }
}

class SavingsAccount extends Account {
    private final int ratePercent;

    SavingsAccount(String owner, long openingBalance, int ratePercent) {
        super(owner, openingBalance);  // must come first
        System.out.println("SavingsAccount constructor for " + owner);
        this.ratePercent = ratePercent;
    }

    void addMonthlyInterest() {
        balance += balance * ratePercent / 100 / 12;  // protected field from Account
    }
}

class CheckingAccount extends Account {
    private final long overdraft;

    CheckingAccount(String owner, long openingBalance, long overdraft) {
        super(owner, openingBalance);
        this.overdraft = overdraft;
    }

    @Override
    long overdraftLimit() {
        return overdraft;
    }
}

/* Output:
Account constructor for Kim
SavingsAccount constructor for Kim
SavingsAccount(Kim) balance=1503
Account constructor for Lee
withdraw 350: true
withdraw 100: false
CheckingAccount(Lee) balance=-250
savings instanceof Account: true
superclass of SavingsAccount: Account
superclass of Account: Object
*/
