// Command (behavioral): encapsulate a request as an object, so requests can
// be queued, logged, passed around and undone.
//
// The invoker (here a change manager) only knows the Command interface:
// execute() and undo(). Each concrete command captures a receiver and the
// arguments it needs, so the invoker can keep a history and roll changes
// back in reverse order when a later step of a change fails.
// JDK examples: Runnable and Callable are commands without undo, and an
// ExecutorService is an invoker that queues them.
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class CommandDemo {
    // The receiver: a firewall that knows nothing about commands
    static final class Firewall {
        final TreeSet<Integer> openPorts = new TreeSet<>(List.of(443));
        final Map<String, String> settings = new TreeMap<>(Map.of("logging", "on"));
    }

    interface Command {
        void execute();
        void undo();
        String describe();
    }

    record OpenPort(Firewall fw, int port) implements Command {
        public void execute() { fw.openPorts.add(port); }
        public void undo() { fw.openPorts.remove(port); }
        public String describe() { return "open port " + port; }
    }

    // Commands that overwrite state must remember the old value to undo
    static final class SetOption implements Command {
        private final Firewall fw;
        private final String key;
        private final String value;
        private String previous;

        SetOption(Firewall fw, String key, String value) {
            this.fw = fw;
            this.key = key;
            this.value = value;
        }

        public void execute() { previous = fw.settings.put(key, value); }
        public void undo() {
            if (previous == null) fw.settings.remove(key); else fw.settings.put(key, previous);
        }
        public String describe() { return "set " + key + "=" + value; }
    }

    // A command whose execution fails, to trigger the rollback
    record HealthCheck(Firewall fw) implements Command {
        public void execute() {
            if (fw.openPorts.contains(23)) throw new IllegalStateException("telnet must stay closed");
        }
        public void undo() {}
        public String describe() { return "health check"; }
    }

    // The invoker: runs commands, records history, rolls back on failure
    static final class ChangeManager {
        private final Deque<Command> history = new ArrayDeque<>();

        boolean apply(List<Command> change) {
            for (Command c : change) {
                try {
                    c.execute();
                    history.push(c);
                    System.out.println("  done: " + c.describe());
                } catch (RuntimeException e) {
                    System.out.println("  FAILED: " + c.describe() + " (" + e.getMessage() + "), rolling back");
                    while (!history.isEmpty()) {
                        Command undone = history.pop();
                        undone.undo();
                        System.out.println("  undone: " + undone.describe());
                    }
                    return false;
                }
            }
            history.clear(); // committed
            return true;
        }
    }

    public static void main(String[] args) {
        Firewall fw = new Firewall();
        ChangeManager manager = new ChangeManager();

        System.out.println("change 1:");
        boolean ok = manager.apply(List.of(
                new OpenPort(fw, 8443), new SetOption(fw, "geo-block", "kp,ir"), new HealthCheck(fw)));
        System.out.println("committed=" + ok + " ports=" + fw.openPorts + " settings=" + fw.settings);

        System.out.println("change 2:");
        ok = manager.apply(List.of(
                new SetOption(fw, "logging", "off"), new OpenPort(fw, 23), new HealthCheck(fw)));
        System.out.println("committed=" + ok + " ports=" + fw.openPorts + " settings=" + fw.settings);
    }
}

/* Output:
change 1:
  done: open port 8443
  done: set geo-block=kp,ir
  done: health check
committed=true ports=[443, 8443] settings={geo-block=kp,ir, logging=on}
change 2:
  done: set logging=off
  done: open port 23
  FAILED: health check (telnet must stay closed), rolling back
  undone: open port 23
  undone: set logging=off
committed=false ports=[443, 8443] settings={geo-block=kp,ir, logging=on}
*/
