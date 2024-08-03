// MET05-J. Ensure that constructors do not call overridable methods
// https://wiki.sei.cmu.edu/confluence/display/java/MET05-J.+Ensure+that+constructors+do+not+call+overridable+methods
//
// A superclass constructor runs before the subclass's field initializers
// and constructor body. If it calls a method that a subclass overrides, the
// override executes on an object whose subclass fields are still null, 0
// or false. A check that reads such a field can then take the wrong branch:
// below, a firewall rule set that is "not configured yet" falls back to
// allow-all. Constructors (and clone, readObject) should call only
// private, final or static methods; do subclass-specific setup in the
// subclass's own constructor, or use a static factory.
import java.util.List;

public class MET05_ConstructorOverridableCalls {
    public static void main(String[] args) {
        Firewall bad = new OfficeFirewallNoncompliant();
        System.out.println("noncompliant: default policy decided in constructor = " + bad.defaultPolicy);

        FirewallCompliant good = new OfficeFirewallCompliant();
        System.out.println("compliant:    default policy = " + good.defaultPolicy());
    }
}

class Firewall {
    final String defaultPolicy;

    Firewall() {
        // Noncompliant: rules() is overridable and runs before the subclass is initialized
        defaultPolicy = rules().isEmpty() ? "ALLOW_ALL" : "DENY_UNLISTED";
    }

    List<String> rules() {
        return List.of();
    }
}

class OfficeFirewallNoncompliant extends Firewall {
    private final List<String> allowed = List.of("10.0.0.0/8:443");

    @Override
    List<String> rules() {
        return allowed == null ? List.of() : allowed; // null while the super constructor runs
    }
}

class FirewallCompliant {
    private final List<String> rules;

    // The subclass passes its data up; nothing overridable is called
    FirewallCompliant(List<String> rules) {
        this.rules = List.copyOf(rules);
    }

    final String defaultPolicy() {
        return rules.isEmpty() ? "ALLOW_ALL" : "DENY_UNLISTED";
    }
}

class OfficeFirewallCompliant extends FirewallCompliant {
    OfficeFirewallCompliant() {
        super(List.of("10.0.0.0/8:443"));
    }
}

/* Output:
noncompliant: default policy decided in constructor = ALLOW_ALL
compliant:    default policy = DENY_UNLISTED
*/
