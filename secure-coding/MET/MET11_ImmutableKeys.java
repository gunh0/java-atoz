// MET11-J. Ensure that keys used in comparison operations are immutable
// https://wiki.sei.cmu.edu/confluence/display/java/MET11-J.+Ensure+that+keys+used+in+comparison+operations+are+immutable
//
// HashMap and HashSet file an entry under the key's hashCode() at insertion
// time; TreeMap and TreeSet place it by compareTo(). If a field that takes
// part in equals/hashCode/compareTo changes afterwards, the entry is still
// in the collection but in the wrong bucket or position: contains() and
// get() no longer find it, remove() cannot delete it. For a deny list this
// fails open: a revoked session is no longer recognized as revoked. Use
// immutable keys (records of immutable values, String, ids), or at least
// keep mutable state out of equals, hashCode and compareTo.
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class MET11_ImmutableKeys {
    // Noncompliant key: mutable fields take part in equals/hashCode
    static final class Session {
        private final String id;
        private String user;

        Session(String id, String user) {
            this.id = id;
            this.user = user;
        }

        void rename(String newUser) {
            this.user = newUser; // e.g. the account was renamed
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Session s && s.id.equals(id) && s.user.equals(user);
        }

        @Override
        public int hashCode() {
            return 31 * id.hashCode() + user.hashCode();
        }
    }

    // Compliant key: an immutable value that identifies the session
    record SessionId(String value) {}

    // A mutable entity whose ORDER key can change
    static final class Account {
        final String name;
        int riskScore;

        Account(String name, int riskScore) {
            this.name = name;
            this.riskScore = riskScore;
        }
    }

    public static void main(String[] args) {
        // A deny list of revoked sessions
        Session stolen = new Session("s-7f3a", "mallory");
        Set<Session> revoked = new HashSet<>();
        revoked.add(stolen);
        System.out.println("noncompliant: revoked before rename: " + revoked.contains(stolen));
        stolen.rename("mallory2");
        System.out.println("noncompliant: revoked after rename:  " + revoked.contains(stolen)
                + " (set still has " + revoked.size() + " entry, remove works: " + revoked.remove(stolen) + ")");

        // Compliant: key the deny list on the immutable identity only
        Set<SessionId> revokedIds = new HashSet<>();
        revokedIds.add(new SessionId(stolen.id));
        System.out.println("compliant:    revoked after rename:  " + revokedIds.contains(new SessionId(stolen.id)));

        // The same failure in a sorted set: the comparison key changes in place
        Comparator<Account> byRisk = Comparator.comparingInt((Account a) -> a.riskScore).thenComparing(a -> a.name);
        Account bob = new Account("bob", 10);
        TreeSet<Account> reviewQueue = new TreeSet<>(byRisk);
        reviewQueue.add(new Account("carol", 50));
        reviewQueue.add(new Account("erin", 30));
        reviewQueue.add(new Account("frank", 70));
        reviewQueue.add(new Account("alice", 5));
        reviewQueue.add(bob);
        bob.riskScore = 99; // mutated while inside the TreeSet
        System.out.println("noncompliant: highest risk first: " + reviewQueue.last().name
                + ", contains bob: " + reviewQueue.contains(bob) + ", remove bob: " + reviewQueue.remove(bob));

        // Compliant: remove, change, re-insert (or key the set on an immutable snapshot)
        Account dave = new Account("dave", 10);
        TreeSet<Account> queue = new TreeSet<>(byRisk);
        queue.add(new Account("carol", 50));
        queue.add(new Account("erin", 30));
        queue.add(new Account("frank", 70));
        queue.add(new Account("alice", 5));
        queue.add(dave);
        queue.remove(dave);
        dave.riskScore = 99;
        queue.add(dave);
        System.out.println("compliant:    highest risk first: " + queue.last().name
                + ", contains dave: " + queue.contains(dave) + ", remove dave: " + queue.remove(dave));
    }
}

/* Output:
noncompliant: revoked before rename: true
noncompliant: revoked after rename:  false (set still has 1 entry, remove works: false)
compliant:    revoked after rename:  true
noncompliant: highest risk first: frank, contains bob: false, remove bob: false
compliant:    highest risk first: dave, contains dave: true, remove dave: true
*/
