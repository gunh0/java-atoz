// An immutable object cannot change after construction, which makes it
// thread-safe without locks, safe to share and cache, and safe as a map key.
// The recipe:
//  - make the class final (or the constructor private) so no subclass adds state
//  - make all fields private final
//  - no setters; "modifiers" return a new instance (withX)
//  - defensively copy mutable inputs in the constructor and mutable outputs
//    in getters (or store unmodifiable copies)
// Collections.unmodifiableList is only a read-only VIEW: changes to the
// backing list show through. List.copyOf takes a real snapshot.
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class ImmutabilityExample {

    // Broken: final fields, but the list and the Date leak out
    static final class LeakyPolicy {
        private final List<String> allowedHosts;
        private final Date expires;

        LeakyPolicy(List<String> allowedHosts, Date expires) {
            this.allowedHosts = allowedHosts; // stores the caller's list
            this.expires = expires;            // stores the caller's Date
        }

        List<String> allowedHosts() {
            return allowedHosts;
        }

        Date expires() {
            return expires;
        }
    }

    // Correct
    static final class Policy {
        private final String name;
        private final List<String> allowedHosts;
        private final long expiresEpochMillis; // store an immutable representation

        Policy(String name, List<String> allowedHosts, Date expires) {
            this.name = name;
            this.allowedHosts = List.copyOf(allowedHosts); // snapshot, unmodifiable
            this.expiresEpochMillis = expires.getTime();
        }

        List<String> allowedHosts() {
            return allowedHosts; // already unmodifiable, safe to return
        }

        Date expires() {
            return new Date(expiresEpochMillis); // fresh copy every time
        }

        Policy withHost(String host) {
            List<String> hosts = new ArrayList<>(allowedHosts);
            hosts.add(host);
            return new Policy(name, hosts, new Date(expiresEpochMillis));
        }

        @Override
        public String toString() {
            return name + allowedHosts + " until " + expiresEpochMillis;
        }
    }

    public static void main(String[] args) {
        List<String> hosts = new ArrayList<>(List.of("api.example.com"));
        Date expiry = new Date(1_700_000_000_000L);

        LeakyPolicy leaky = new LeakyPolicy(hosts, expiry);
        Policy policy = new Policy("prod", hosts, expiry);

        // Attacker-controlled code mutates what it handed in and what it got back
        hosts.add("evil.example.net");
        expiry.setTime(4_000_000_000_000L);
        leaky.allowedHosts().add("also-evil.example.net");

        System.out.println("leaky hosts:   " + leaky.allowedHosts());
        System.out.println("leaky expires: " + leaky.expires().getTime());
        System.out.println("policy:        " + policy);

        try {
            policy.allowedHosts().add("evil.example.net");
        } catch (UnsupportedOperationException e) {
            System.out.println("policy.allowedHosts().add -> UnsupportedOperationException");
        }
        policy.expires().setTime(0); // modifies only the copy
        System.out.println("policy after Date poke: " + policy);

        // "Modifying" returns a new object; the original stays the same
        Policy extended = policy.withHost("cdn.example.com");
        System.out.println("original: " + policy);
        System.out.println("extended: " + extended);

        // Read-only view vs snapshot
        List<String> backing = new ArrayList<>(List.of("a", "b"));
        List<String> view = Collections.unmodifiableList(backing);
        List<String> snapshot = List.copyOf(backing);
        backing.add("c");
        System.out.println("unmodifiable view: " + view + ", List.copyOf snapshot: " + snapshot);
    }
}

/* Output:
leaky hosts:   [api.example.com, evil.example.net, also-evil.example.net]
leaky expires: 4000000000000
policy:        prod[api.example.com] until 1700000000000
policy.allowedHosts().add -> UnsupportedOperationException
policy after Date poke: prod[api.example.com] until 1700000000000
original: prod[api.example.com] until 1700000000000
extended: prod[api.example.com, cdn.example.com] until 1700000000000
unmodifiable view: [a, b, c], List.copyOf snapshot: [a, b]
*/
