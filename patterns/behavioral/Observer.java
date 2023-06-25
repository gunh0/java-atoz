// Observer (behavioral): define a one-to-many dependency between objects so
// that when one object changes state, all its dependents are notified and
// updated automatically.
//
// Here a login monitor (the subject) publishes every authentication event to
// whoever subscribed: a lockout policy, an alerting rule and an audit log.
// The subject only knows the Listener interface, so observers can be added or
// removed at runtime without touching it. subscribe() returns a handle that
// unsubscribes, and the listener list is copied before notifying so a
// listener may unsubscribe itself during a callback.
// JDK examples: java.beans.PropertyChangeListener, Swing/AWT event listeners
// and java.util.concurrent.Flow (reactive streams with back-pressure).
// java.util.Observer/Observable also exist but are deprecated since Java 9.
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class ObserverDemo {
    record LoginEvent(String user, String sourceIp, boolean success) {}

    @FunctionalInterface
    interface Listener {
        void onEvent(LoginEvent event);
    }

    // The subject
    static final class LoginMonitor {
        // CopyOnWriteArrayList: iteration works on a snapshot, so listeners
        // may (un)subscribe while an event is being delivered
        private final List<Listener> listeners = new CopyOnWriteArrayList<>();

        Runnable subscribe(Listener l) {
            listeners.add(l);
            return () -> listeners.remove(l);
        }

        void publish(LoginEvent event) {
            for (Listener l : listeners) l.onEvent(event);
        }
    }

    // Observer 1: locks an account after 3 consecutive failures
    static final class LockoutPolicy implements Listener {
        private final Map<String, Integer> failures = new HashMap<>();

        public void onEvent(LoginEvent e) {
            if (e.success()) {
                failures.remove(e.user());
                return;
            }
            int n = failures.merge(e.user(), 1, Integer::sum);
            if (n == 3) System.out.println("  [lockout] locking " + e.user() + " after 3 failures");
        }
    }

    // Observer 2: a one-shot alert that unsubscribes itself after firing
    static final class NewAdminLoginAlert implements Listener {
        Runnable unsubscribe = () -> {};

        public void onEvent(LoginEvent e) {
            if (e.success() && e.user().equals("admin")) {
                System.out.println("  [alert] admin logged in from " + e.sourceIp() + " (alert disarmed)");
                unsubscribe.run();
            }
        }
    }

    public static void main(String[] args) {
        LoginMonitor monitor = new LoginMonitor();
        List<String> auditLog = new ArrayList<>();

        monitor.subscribe(new LockoutPolicy());
        NewAdminLoginAlert alert = new NewAdminLoginAlert();
        alert.unsubscribe = monitor.subscribe(alert);
        // Observer 3: a lambda
        Runnable stopAudit = monitor.subscribe(e -> auditLog.add(e.user() + "@" + e.sourceIp() + (e.success() ? " ok" : " fail")));

        List<LoginEvent> events = List.of(
                new LoginEvent("alice", "10.0.0.7", true),
                new LoginEvent("bob", "203.0.113.9", false),
                new LoginEvent("bob", "203.0.113.9", false),
                new LoginEvent("admin", "10.0.0.2", true),
                new LoginEvent("bob", "203.0.113.9", false),
                new LoginEvent("admin", "198.51.100.4", true));

        for (int i = 0; i < events.size(); i++) {
            if (i == 5) {
                stopAudit.run();
                System.out.println("  (audit log unsubscribed)");
            }
            System.out.println("event " + (i + 1) + ": " + events.get(i));
            monitor.publish(events.get(i));
        }
        System.out.println("audit log: " + auditLog);
    }
}

/* Output:
event 1: LoginEvent[user=alice, sourceIp=10.0.0.7, success=true]
event 2: LoginEvent[user=bob, sourceIp=203.0.113.9, success=false]
event 3: LoginEvent[user=bob, sourceIp=203.0.113.9, success=false]
event 4: LoginEvent[user=admin, sourceIp=10.0.0.2, success=true]
  [alert] admin logged in from 10.0.0.2 (alert disarmed)
event 5: LoginEvent[user=bob, sourceIp=203.0.113.9, success=false]
  [lockout] locking bob after 3 failures
  (audit log unsubscribed)
event 6: LoginEvent[user=admin, sourceIp=198.51.100.4, success=true]
audit log: [alice@10.0.0.7 ok, bob@203.0.113.9 fail, bob@203.0.113.9 fail, admin@10.0.0.2 ok, bob@203.0.113.9 fail]
*/
