// A class is a blueprint: it declares the state (fields) and behaviour
// (methods) that every object of that type will have. An object is one
// instance created from the blueprint with `new`, living on the heap.
//  - each object gets its OWN copy of the instance fields
//  - variables hold references, so two variables can point at one object
//  - fields get default values (0, false, null) if you do not set them
//  - == compares references; it tells you "same object", not "same content"
public class ClassAndObjectExample {
    public static void main(String[] args) {
        // Two objects from the same class, each with its own state
        BankAccount alice = new BankAccount();
        alice.owner = "Alice";
        alice.deposit(100);

        BankAccount bob = new BankAccount();
        bob.owner = "Bob";
        bob.deposit(40);
        bob.withdraw(15);

        System.out.println(alice.describe());
        System.out.println(bob.describe());

        // A fresh object: fields hold their default values
        BankAccount blank = new BankAccount();
        System.out.println("blank: owner=" + blank.owner + ", balance=" + blank.balance
                + ", frozen=" + blank.frozen);

        // Copying a reference does not copy the object
        BankAccount alias = alice;
        alias.deposit(1);
        System.out.println("alice after deposit through alias: " + alice.balance);
        System.out.println("alias == alice: " + (alias == alice));
        System.out.println("alice == bob: " + (alice == bob));

        // Objects are passed to methods by copying the reference
        freeze(bob);
        System.out.println("bob frozen after freeze(bob): " + bob.frozen);
        System.out.println("bob withdraw while frozen: " + bob.withdraw(5));
    }

    static void freeze(BankAccount account) {
        account.frozen = true; // changes the caller's object
        account = null;        // only changes the local copy of the reference
    }
}

class BankAccount {
    // State: instance fields
    String owner;
    long balance;
    boolean frozen;

    // Behaviour: instance methods operate on this object's fields
    void deposit(long amount) {
        balance += amount;
    }

    boolean withdraw(long amount) {
        if (frozen || amount > balance) {
            return false;
        }
        balance -= amount;
        return true;
    }

    String describe() {
        return owner + " has " + balance;
    }
}

/* Output:
Alice has 100
Bob has 25
blank: owner=null, balance=0, frozen=false
alice after deposit through alias: 101
alias == alice: true
alice == bob: false
bob frozen after freeze(bob): true
bob withdraw while frozen: false
*/
