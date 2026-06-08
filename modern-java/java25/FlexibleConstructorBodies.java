// JEP 513 — Flexible Constructor Bodies (Java 25)
//
// Until Java 25, super(...) or this(...) had to be the first statement of a
// constructor. Checking arguments or computing values first meant tricks:
// a static helper called inside the super(...) argument list, or a private
// constructor plus a factory method. Worse, a subclass could not set its own
// fields before the superclass constructor ran, so an overridable method
// called from that constructor saw null and 0 (Effective Java item 19).
// Now statements may come before the explicit constructor call. This
// "prologue" may not use the object under construction (no reading fields,
// no instance method calls, no passing `this`; javac rejects these), with
// one exception: it may assign this class's fields that have no
// initializer. So arguments are validated before any superclass code runs,
// and fields are set before the superclass can observe them. A check that
// throws in the prologue means no object ever escapes, the cleanest answer
// to the finalizer attack in secure-coding/OBJ/OBJ11_ConstructorExceptions.java.
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FlexibleConstructorBodies {

    // 1. A superclass constructor that calls an overridable method
    static class Widget {
        Widget() {
            System.out.println("  Widget() renders: " + render());
        }

        String render() {
            return "widget";
        }
    }

    static class OldLabel extends Widget {
        private final String text;

        OldLabel(String text) {
            super(); // must come first, so render() runs before text is set
            this.text = text;
        }

        @Override
        String render() {
            return "label(" + text + ")";
        }
    }

    static class NewLabel extends Widget {
        private final String text;

        NewLabel(String text) {
            this.text = Objects.requireNonNull(text); // allowed: a field without initializer
            super();
        }

        @Override
        String render() {
            return "label(" + text + ")";
        }
    }

    // 2. A superclass constructor with a side effect: it registers the object
    static abstract class Endpoint {
        static final List<String> REGISTRY = new ArrayList<>();

        Endpoint(String name) {
            REGISTRY.add(name);
        }
    }

    static class OldPort extends Endpoint {
        OldPort(int port) {
            super("port " + port);
            if (port < 1 || port > 65535) throw new IllegalArgumentException("bad port " + port);
        }
    }

    static class NewPort extends Endpoint {
        NewPort(int port) {
            if (port < 1 || port > 65535) throw new IllegalArgumentException("bad port " + port);
            super("port " + port);
        }
    }

    // 3. Computing arguments for this(...): parse once, pass two values
    record HostPort(String host, int port) {
        HostPort(String spec) {
            int colon = spec.lastIndexOf(':');
            if (colon <= 0) throw new IllegalArgumentException("expected host:port, got " + spec);
            this(spec.substring(0, colon), Integer.parseInt(spec.substring(colon + 1)));
        }
    }

    public static void main(String[] args) {
        System.out.println("field set after super():");
        new OldLabel("OK");
        System.out.println("field set before super():");
        new NewLabel("OK");

        for (int port : new int[] {443, 70000}) {
            try {
                new OldPort(port);
            } catch (IllegalArgumentException e) {
                System.out.println("OldPort: " + e.getMessage());
            }
        }
        System.out.println("registry after OldPort: " + Endpoint.REGISTRY);

        Endpoint.REGISTRY.clear();
        for (int port : new int[] {443, 70000}) {
            try {
                new NewPort(port);
            } catch (IllegalArgumentException e) {
                System.out.println("NewPort: " + e.getMessage());
            }
        }
        System.out.println("registry after NewPort: " + Endpoint.REGISTRY);

        System.out.println(new HostPort("example.org:8443"));
        try {
            new HostPort("no-port-here");
        } catch (IllegalArgumentException e) {
            System.out.println("HostPort: " + e.getMessage());
        }
    }
}

/* Output:
field set after super():
  Widget() renders: label(null)
field set before super():
  Widget() renders: label(OK)
OldPort: bad port 70000
registry after OldPort: [port 443, port 70000]
NewPort: bad port 70000
registry after NewPort: [port 443]
HostPort[host=example.org, port=8443]
HostPort: expected host:port, got no-port-here
*/
