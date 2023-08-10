// State (behavioral): allow an object to alter its behavior when its internal
// state changes. The object will appear to change its class.
//
// Here a circuit breaker guards calls to a flaky downstream service. Its
// behaviour depends entirely on which state it is in:
//   CLOSED     calls pass through; too many failures in a row -> OPEN
//   OPEN       calls fail fast without touching the service; after a
//              cool-down -> HALF_OPEN
//   HALF_OPEN  one trial call: success -> CLOSED, failure -> OPEN again
// Each state is its own class that handles call() and decides the next
// state, so the breaker (the context) has no switch over a status field.
// Time is a tick counter so the example is deterministic.
// Compared with an enum + switch in every method, adding a state means adding
// a class, not editing every method of the context.
import java.util.List;
import java.util.function.BooleanSupplier;

public class StateDemo {
    interface State {
        String call(CircuitBreaker cb, BooleanSupplier service);
        String name();
    }

    static final class Closed implements State {
        private int consecutiveFailures;

        public String call(CircuitBreaker cb, BooleanSupplier service) {
            if (service.getAsBoolean()) {
                consecutiveFailures = 0;
                return "ok";
            }
            if (++consecutiveFailures >= CircuitBreaker.FAILURE_THRESHOLD) {
                cb.transition(new Open(cb.now));
                return "error (tripped)";
            }
            return "error";
        }

        public String name() { return "CLOSED"; }
    }

    static final class Open implements State {
        private final int openedAt;

        Open(int openedAt) { this.openedAt = openedAt; }

        public String call(CircuitBreaker cb, BooleanSupplier service) {
            if (cb.now - openedAt >= CircuitBreaker.COOL_DOWN) {
                cb.transition(new HalfOpen());
                return cb.state.call(cb, service); // let the new state handle it
            }
            return "rejected (fail fast)";
        }

        public String name() { return "OPEN"; }
    }

    static final class HalfOpen implements State {
        public String call(CircuitBreaker cb, BooleanSupplier service) {
            if (service.getAsBoolean()) {
                cb.transition(new Closed());
                return "ok (trial passed)";
            }
            cb.transition(new Open(cb.now));
            return "error (trial failed)";
        }

        public String name() { return "HALF_OPEN"; }
    }

    // The context: delegates every call to its current state object
    static final class CircuitBreaker {
        static final int FAILURE_THRESHOLD = 3;
        static final int COOL_DOWN = 3;
        State state = new Closed();
        int now;
        final StringBuilder transitions = new StringBuilder();

        void transition(State next) {
            transitions.append("  [").append(state.name()).append(" -> ").append(next.name()).append(']');
            state = next;
        }

        String call(BooleanSupplier service) {
            return state.call(this, service);
        }
    }

    public static void main(String[] args) {
        // Whether the downstream service is healthy at each tick
        List<Boolean> healthy = List.of(true, false, false, false, false, false, false, false, false, true, true);
        CircuitBreaker cb = new CircuitBreaker();
        int[] serviceCalls = {0};
        for (int t = 0; t < healthy.size(); t++) {
            cb.now = t;
            boolean up = healthy.get(t);
            String result = cb.call(() -> {
                serviceCalls[0]++;
                return up;
            });
            String line = String.format("t=%2d %-5s %-21s%s", t, up ? "up" : "down", result, cb.transitions);
            System.out.println(line.stripTrailing());
            cb.transitions.setLength(0);
        }
        System.out.println("final state: " + cb.state.name()
                + ", service actually called " + serviceCalls[0] + " of " + healthy.size() + " times");
    }
}

/* Output:
t= 0 up    ok
t= 1 down  error
t= 2 down  error
t= 3 down  error (tripped)        [CLOSED -> OPEN]
t= 4 down  rejected (fail fast)
t= 5 down  rejected (fail fast)
t= 6 down  error (trial failed)   [OPEN -> HALF_OPEN]  [HALF_OPEN -> OPEN]
t= 7 down  rejected (fail fast)
t= 8 down  rejected (fail fast)
t= 9 up    ok (trial passed)      [OPEN -> HALF_OPEN]  [HALF_OPEN -> CLOSED]
t=10 up    ok
final state: CLOSED, service actually called 7 of 11 times
*/
