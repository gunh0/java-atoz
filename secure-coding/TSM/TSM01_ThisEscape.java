// TSM01-J. Do not let the this reference escape during object construction
// https://wiki.sei.cmu.edu/confluence/display/java/TSM01-J.+Do+not+let+the+this+reference+escape+during+object+construction
//
// Until a constructor returns, the object is not fully initialized: final
// fields may still hold their default values and invariants do not hold
// yet. A constructor that publishes this (registers it as a listener,
// stores it in a static field, starts a thread with it, calls an
// overridable method) lets other code use the half-built object; with
// threads, even final-field guarantees are lost. Here an event bus replays
// the latest event to every new listener immediately, so the listener runs
// before its policy field is set and lets a forbidden event through.
// Construct first, publish afterwards: a private constructor plus a static
// factory method that registers the finished object.
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TSM01_ThisEscape {
    public static void main(String[] args) {
        EventBus bus = new EventBus();
        bus.publish("delete-all");

        System.out.println("noncompliant:");
        new GuardNoncompliant(bus, "delete-all");
        System.out.println("compliant:");
        GuardCompliant.create(bus, "delete-all");
    }
}

// Replays the most recent event to each listener as it registers
class EventBus {
    private final List<Consumer<String>> listeners = new ArrayList<>();
    private String last;

    void register(Consumer<String> listener) {
        listeners.add(listener);
        if (last != null) {
            listener.accept(last);
        }
    }

    void publish(String event) {
        last = event;
        listeners.forEach(l -> l.accept(event));
    }
}

// Noncompliant: this escapes before forbidden is assigned
class GuardNoncompliant {
    private final String forbidden;

    GuardNoncompliant(EventBus bus, String forbidden) {
        bus.register(this::onEvent); // this escapes here
        this.forbidden = forbidden;
    }

    void onEvent(String event) {
        System.out.println("  " + event + " (forbidden = " + forbidden + ") -> "
                + (event.equals(forbidden) ? "blocked" : "allowed"));
    }
}

// Compliant: fully constructed before it is registered
class GuardCompliant {
    private final String forbidden;

    private GuardCompliant(String forbidden) {
        this.forbidden = forbidden;
    }

    static GuardCompliant create(EventBus bus, String forbidden) {
        GuardCompliant guard = new GuardCompliant(forbidden);
        bus.register(guard::onEvent);
        return guard;
    }

    void onEvent(String event) {
        System.out.println("  " + event + " (forbidden = " + forbidden + ") -> "
                + (event.equals(forbidden) ? "blocked" : "allowed"));
    }
}

/* Output:
noncompliant:
  delete-all (forbidden = null) -> allowed
compliant:
  delete-all (forbidden = delete-all) -> blocked
*/
