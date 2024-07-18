// ERR03-J. Restore prior object state on method failure
// https://wiki.sei.cmu.edu/confluence/display/java/ERR03-J.+Restore+prior+object+state+on+method+failure
//
// A method that changes an object's state in several steps and then
// throws halfway leaves the object inconsistent: money debited but not
// credited, a counter incremented for an operation that never happened, a
// user marked "verified" before verification failed. Callers that catch
// the exception and carry on now work with corrupt state. Make failure
// atomic: validate everything before mutating, work on a copy and commit
// at the end, or roll back the changes in a catch/finally block before
// rethrowing.
import java.util.HashMap;
import java.util.Map;

public class ERR03_RestoreStateOnFailure {
    public static void main(String[] args) {
        Bank bad = new Bank();
        try {
            bad.transferNoncompliant("alice", "bob", 30);
            bad.transferNoncompliant("alice", "mallory", 50); // unknown account
        } catch (IllegalArgumentException e) {
            System.out.println("noncompliant: " + e.getMessage() + " -> " + bad);
        }

        Bank good = new Bank();
        try {
            good.transferCompliant("alice", "bob", 30);
            good.transferCompliant("alice", "mallory", 50);
        } catch (IllegalArgumentException e) {
            System.out.println("compliant:    " + e.getMessage() + " -> " + good);
        }

        Bank rollback = new Bank();
        try {
            rollback.transferWithRollback("alice", "bob", 30);
            rollback.transferWithRollback("alice", "mallory", 50);
        } catch (IllegalArgumentException e) {
            System.out.println("rollback:     " + e.getMessage() + " -> " + rollback);
        }
    }
}

class Bank {
    private final Map<String, Integer> balances = new HashMap<>(Map.of("alice", 100, "bob", 100));

    // Noncompliant: debits first, then discovers the target does not exist
    void transferNoncompliant(String from, String to, int amount) {
        balances.put(from, balances.get(from) - amount);
        credit(to, amount); // throws for an unknown account
    }

    // Compliant: all checks before any mutation
    void transferCompliant(String from, String to, int amount) {
        if (!balances.containsKey(from) || !balances.containsKey(to)) {
            throw new IllegalArgumentException("unknown account");
        }
        if (amount <= 0 || balances.get(from) < amount) {
            throw new IllegalArgumentException("invalid amount");
        }
        balances.put(from, balances.get(from) - amount);
        balances.put(to, balances.get(to) + amount);
    }

    // Compliant when up-front checks are impossible: undo on failure
    void transferWithRollback(String from, String to, int amount) {
        int before = balances.get(from);
        balances.put(from, before - amount);
        try {
            credit(to, amount);
        } catch (RuntimeException e) {
            balances.put(from, before);
            throw e;
        }
    }

    private void credit(String to, int amount) {
        Integer current = balances.get(to);
        if (current == null) {
            throw new IllegalArgumentException("unknown account");
        }
        balances.put(to, current + amount);
    }

    @Override
    public String toString() {
        int total = balances.values().stream().mapToInt(Integer::intValue).sum();
        return "alice=" + balances.get("alice") + ", bob=" + balances.get("bob") + ", total=" + total;
    }
}

/* Output:
noncompliant: unknown account -> alice=20, bob=130, total=150
compliant:    unknown account -> alice=70, bob=130, total=200
rollback:     unknown account -> alice=70, bob=130, total=200
*/
