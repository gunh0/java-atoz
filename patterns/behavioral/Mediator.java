// Mediator (behavioral): define an object that encapsulates how a set of
// objects interact. The mediator promotes loose coupling by keeping objects
// from referring to each other explicitly, and it lets you vary their
// interaction independently.
//
// Here an incident-response setup has four components (colleagues): an IDS
// sensor, a firewall, a ticket system and an on-call pager. None of them
// knows the others; each only reports to the mediator, which holds the
// playbook ("high severity: block, open a ticket, page someone; a block
// failed: escalate"). Changing the playbook means changing one class, not
// rewiring N components that point at each other (N*(N-1) links -> N).
// Often-cited JDK examples: java.util.Timer and Executor.execute(), which sit
// between the code that submits tasks and the threads that run them.
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MediatorDemo {
    // What colleagues can tell the mediator
    sealed interface Event permits Detection, BlockFailed, TicketResolved {}
    record Detection(String sourceIp, String signature, int severity) implements Event {}
    record BlockFailed(String sourceIp, String reason) implements Event {}
    record TicketResolved(int ticketId, String sourceIp) implements Event {}

    interface Mediator {
        void notify(Object sender, Event event);
    }

    // Colleagues: each keeps a reference to the mediator only
    abstract static class Component {
        protected final Mediator mediator;

        Component(Mediator mediator) { this.mediator = mediator; }

        void log(String message) {
            System.out.printf("  %-9s %s%n", getClass().getSimpleName(), message);
        }
    }

    static final class Ids extends Component {
        Ids(Mediator m) { super(m); }

        void observe(String ip, String signature, int severity) {
            log("detected " + signature + " from " + ip + " (severity " + severity + ")");
            mediator.notify(this, new Detection(ip, signature, severity));
        }
    }

    static final class Firewall extends Component {
        private final Set<String> protectedRanges = Set.of("10.0.0.1"); // e.g. the gateway itself
        final List<String> blocked = new ArrayList<>();

        Firewall(Mediator m) { super(m); }

        void block(String ip) {
            if (protectedRanges.contains(ip)) {
                log("refusing to block " + ip);
                mediator.notify(this, new BlockFailed(ip, "address is allow-listed"));
                return;
            }
            blocked.add(ip);
            log("blocked " + ip);
        }

        void unblock(String ip) {
            blocked.remove(ip);
            log("unblocked " + ip);
        }
    }

    static final class Tickets extends Component {
        private int nextId = 100;

        Tickets(Mediator m) { super(m); }

        int open(String title) {
            int id = nextId++;
            log("opened #" + id + ": " + title);
            return id;
        }

        void resolve(int id, String ip) {
            log("resolved #" + id);
            mediator.notify(this, new TicketResolved(id, ip));
        }
    }

    static final class Pager extends Component {
        Pager(Mediator m) { super(m); }

        void page(String message) { log("paging on-call: " + message); }
    }

    // The concrete mediator: the only place that knows the playbook
    static final class IncidentPlaybook implements Mediator {
        final Ids ids = new Ids(this);
        final Firewall firewall = new Firewall(this);
        final Tickets tickets = new Tickets(this);
        final Pager pager = new Pager(this);

        public void notify(Object sender, Event event) {
            switch (event) {
                case Detection d when d.severity() >= 8 -> {
                    tickets.open(d.signature() + " from " + d.sourceIp());
                    firewall.block(d.sourceIp());
                    pager.page(d.signature());
                }
                case Detection d -> tickets.open("review " + d.signature() + " from " + d.sourceIp());
                case BlockFailed f -> pager.page("manual action needed for " + f.sourceIp() + ": " + f.reason());
                case TicketResolved r -> firewall.unblock(r.sourceIp());
            }
        }
    }

    public static void main(String[] args) {
        IncidentPlaybook playbook = new IncidentPlaybook();

        System.out.println("low severity:");
        playbook.ids.observe("198.51.100.23", "port scan", 3);
        System.out.println("high severity:");
        playbook.ids.observe("203.0.113.66", "sql injection", 9);
        System.out.println("high severity from an allow-listed address:");
        playbook.ids.observe("10.0.0.1", "brute force", 8);
        System.out.println("analyst closes the ticket:");
        playbook.tickets.resolve(101, "203.0.113.66");

        System.out.println("firewall block list: " + playbook.firewall.blocked);
    }
}

/* Output:
low severity:
  Ids       detected port scan from 198.51.100.23 (severity 3)
  Tickets   opened #100: review port scan from 198.51.100.23
high severity:
  Ids       detected sql injection from 203.0.113.66 (severity 9)
  Tickets   opened #101: sql injection from 203.0.113.66
  Firewall  blocked 203.0.113.66
  Pager     paging on-call: sql injection
high severity from an allow-listed address:
  Ids       detected brute force from 10.0.0.1 (severity 8)
  Tickets   opened #102: brute force from 10.0.0.1
  Firewall  refusing to block 10.0.0.1
  Pager     paging on-call: manual action needed for 10.0.0.1: address is allow-listed
  Pager     paging on-call: brute force
analyst closes the ticket:
  Tickets   resolved #101
  Firewall  unblocked 203.0.113.66
firewall block list: []
*/
