// Factory Method (creational): define an operation that creates an object,
// but let subclasses decide which concrete class gets instantiated.
//
// The base class owns the algorithm and talks only to an interface; the one
// step that needs a concrete type is a "factory method" that subclasses
// override. Here an audit-log exporter always does the same thing (header,
// one line per event, footer), and each subclass supplies the formatter.
// JDK example: Iterable.iterator() is a factory method. AbstractList, for
// instance, implements its algorithms on top of iterators that each concrete
// collection creates in its own way.
import java.util.List;

public class FactoryMethodDemo {
    record AuditEvent(String user, String action, boolean allowed) {}

    // The product interface
    interface EventFormatter {
        String header();
        String format(AuditEvent event);
        String footer(int count);
    }

    // The creator: the export algorithm is fixed, the formatter is not
    abstract static class AuditExporter {
        // the factory method
        protected abstract EventFormatter createFormatter();

        final String export(List<AuditEvent> events) {
            EventFormatter formatter = createFormatter();
            StringBuilder out = new StringBuilder(formatter.header());
            for (AuditEvent e : events) {
                out.append(formatter.format(e));
            }
            return out.append(formatter.footer(events.size())).toString();
        }
    }

    static final class CsvExporter extends AuditExporter {
        @Override
        protected EventFormatter createFormatter() {
            return new EventFormatter() {
                public String header() { return "user,action,allowed\n"; }
                public String format(AuditEvent e) { return e.user() + "," + e.action() + "," + e.allowed() + "\n"; }
                public String footer(int count) { return ""; }
            };
        }
    }

    static final class JsonLinesExporter extends AuditExporter {
        @Override
        protected EventFormatter createFormatter() {
            return new EventFormatter() {
                public String header() { return ""; }
                public String format(AuditEvent e) {
                    return "{\"user\":\"" + e.user() + "\",\"action\":\"" + e.action()
                            + "\",\"allowed\":" + e.allowed() + "}\n";
                }
                public String footer(int count) { return ""; }
            };
        }
    }

    // Adding a format later means adding one subclass; export() is untouched
    static final class SyslogExporter extends AuditExporter {
        @Override
        protected EventFormatter createFormatter() {
            return new EventFormatter() {
                public String header() { return ""; }
                public String format(AuditEvent e) {
                    int severity = e.allowed() ? 6 : 4; // informational / warning
                    // syslog priority = facility * 8 + severity, facility 10 = authpriv
                    return "<" + (10 * 8 + severity) + "> audit: " + e.user() + " " + e.action()
                            + (e.allowed() ? "" : " DENIED") + "\n";
                }
                public String footer(int count) { return "<86> audit: " + count + " events exported\n"; }
            };
        }
    }

    public static void main(String[] args) {
        List<AuditEvent> events = List.of(
                new AuditEvent("alice", "login", true),
                new AuditEvent("bob", "delete-user", false),
                new AuditEvent("alice", "rotate-key", true));

        // Client code depends only on the abstract creator
        List<AuditExporter> exporters = List.of(new CsvExporter(), new JsonLinesExporter(), new SyslogExporter());
        for (AuditExporter exporter : exporters) {
            System.out.println("== " + exporter.getClass().getSimpleName());
            System.out.print(exporter.export(events));
        }

        // The JDK's factory method: each collection creates its own iterator type
        System.out.println("== Iterable.iterator()");
        System.out.println("ArrayList -> " + new java.util.ArrayList<>(List.of(1)).iterator().getClass().getName());
        System.out.println("ArrayDeque -> " + new java.util.ArrayDeque<>(List.of(1)).iterator().getClass().getName());
    }
}

/* Output:
== CsvExporter
user,action,allowed
alice,login,true
bob,delete-user,false
alice,rotate-key,true
== JsonLinesExporter
{"user":"alice","action":"login","allowed":true}
{"user":"bob","action":"delete-user","allowed":false}
{"user":"alice","action":"rotate-key","allowed":true}
== SyslogExporter
<86> audit: alice login
<84> audit: bob delete-user DENIED
<86> audit: alice rotate-key
<86> audit: 3 events exported
== Iterable.iterator()
ArrayList -> java.util.ArrayList$Itr
ArrayDeque -> java.util.ArrayDeque$DeqIterator
*/
