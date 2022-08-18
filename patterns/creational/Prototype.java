// Prototype (creational): create new objects by copying a fully configured
// existing object (the prototype) instead of building them from scratch.
//
// Useful when an object is expensive or fiddly to set up and many variants
// differ only slightly. Here a registry holds pre-configured firewall rule
// sets; each new tenant gets a copy of a template and then customizes it.
// The copy must be DEEP enough: sharing the mutable rule list between the
// template and the copy would let one tenant change everyone's firewall.
// Java's Object.clone() is awkward (see Effective Java Item 13), so this file
// uses a copy constructor behind a copy() method. JDK example: ArrayList's
// copy constructor and clone() (a shallow copy of the backing array).
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PrototypeDemo {
    record Rule(String action, int port, String source) {
        @Override
        public String toString() { return action + " " + port + " from " + source; }
    }

    interface Prototype<T> {
        T copy();
    }

    static final class RuleSet implements Prototype<RuleSet> {
        private final String name;
        private final List<Rule> rules; // mutable, so it must not be shared

        RuleSet(String name) {
            this.name = name;
            this.rules = new ArrayList<>();
        }

        // Copy constructor: the list is copied, the immutable Rule records can be shared
        private RuleSet(RuleSet source, String name) {
            this.name = name;
            this.rules = new ArrayList<>(source.rules);
        }

        RuleSet allow(int port, String source) { rules.add(new Rule("ALLOW", port, source)); return this; }
        RuleSet deny(int port, String source) { rules.add(new Rule("DENY", port, source)); return this; }

        @Override
        public RuleSet copy() { return new RuleSet(this, name + "-copy"); }

        RuleSet copyAs(String newName) { return new RuleSet(this, newName); }

        @Override
        public String toString() { return name + " " + rules; }
    }

    // A prototype registry: clients ask for a template by name and get a copy
    static final class TemplateRegistry {
        private final Map<String, RuleSet> templates = new LinkedHashMap<>();

        void register(String key, RuleSet template) { templates.put(key, template); }

        RuleSet newFrom(String key, String name) {
            RuleSet template = templates.get(key);
            if (template == null) throw new IllegalArgumentException("no template " + key);
            return template.copyAs(name);
        }
    }

    // What goes wrong with a shallow copy that shares the list
    static final class ShallowRuleSet {
        final List<Rule> rules;
        ShallowRuleSet(List<Rule> rules) { this.rules = rules; }
        ShallowRuleSet copy() { return new ShallowRuleSet(rules); }
    }

    public static void main(String[] args) {
        TemplateRegistry registry = new TemplateRegistry();
        registry.register("web", new RuleSet("web-template")
                .allow(443, "0.0.0.0/0").allow(80, "0.0.0.0/0").deny(22, "0.0.0.0/0"));
        registry.register("db", new RuleSet("db-template")
                .allow(5432, "10.0.0.0/8").deny(5432, "0.0.0.0/0"));

        RuleSet tenantA = registry.newFrom("web", "tenant-a-web");
        RuleSet tenantB = registry.newFrom("web", "tenant-b-web").allow(22, "203.0.113.7/32");
        RuleSet tenantC = registry.newFrom("db", "tenant-c-db");

        System.out.println(tenantA);
        System.out.println(tenantB);
        System.out.println(tenantC);
        System.out.println("template untouched: " + registry.newFrom("web", "check"));

        // The shallow version leaks one tenant's change into the template
        ShallowRuleSet template = new ShallowRuleSet(new ArrayList<>(List.of(new Rule("DENY", 22, "0.0.0.0/0"))));
        ShallowRuleSet shallow = template.copy();
        shallow.rules.add(new Rule("ALLOW", 22, "0.0.0.0/0"));
        System.out.println("shallow copy changed the template too: " + template.rules);
    }
}

/* Output:
tenant-a-web [ALLOW 443 from 0.0.0.0/0, ALLOW 80 from 0.0.0.0/0, DENY 22 from 0.0.0.0/0]
tenant-b-web [ALLOW 443 from 0.0.0.0/0, ALLOW 80 from 0.0.0.0/0, DENY 22 from 0.0.0.0/0, ALLOW 22 from 203.0.113.7/32]
tenant-c-db [ALLOW 5432 from 10.0.0.0/8, DENY 5432 from 0.0.0.0/0]
template untouched: check [ALLOW 443 from 0.0.0.0/0, ALLOW 80 from 0.0.0.0/0, DENY 22 from 0.0.0.0/0]
shallow copy changed the template too: [DENY 22 from 0.0.0.0/0, ALLOW 22 from 0.0.0.0/0]
*/
