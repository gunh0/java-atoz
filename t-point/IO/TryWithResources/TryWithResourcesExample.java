// try-with-resources closes every resource declared in its header, in reverse
// order of declaration, whether the block completes normally or throws.
//  - any AutoCloseable works: streams, readers, sockets, locks you wrap, ...
//  - if the block throws AND close() throws, the block's exception wins and the
//    close exception is attached to it as a suppressed exception
//    (try/finally would lose the original one)
//  - since Java 9 an effectively final variable can be listed: try (in) { ... }
// It replaces the error-prone try/finally/close/null-check pattern.
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class TryWithResourcesExample {
    // A resource that reports when it is opened and closed, and can fail on close
    static final class AuditChannel implements AutoCloseable {
        private final String name;
        private final boolean failOnClose;

        AuditChannel(String name, boolean failOnClose) {
            this.name = name;
            this.failOnClose = failOnClose;
            System.out.println("  open " + name);
        }

        void send(String event) {
            if (event.isEmpty()) throw new IllegalArgumentException("empty event on " + name);
            System.out.println("  " + name + " <- " + event);
        }

        @Override
        public void close() {
            System.out.println("  close " + name);
            if (failOnClose) throw new IllegalStateException("flush failed on " + name);
        }
    }

    public static void main(String[] args) throws IOException {
        System.out.println("1) normal completion, closed in reverse order:");
        try (AuditChannel a = new AuditChannel("syslog", false);
             AuditChannel b = new AuditChannel("siem", false)) {
            a.send("login alice");
            b.send("login alice");
        }

        System.out.println("2) body throws, close throws too:");
        try (AuditChannel c = new AuditChannel("siem", true)) {
            c.send("");
        } catch (IllegalArgumentException e) {
            System.out.println("  caught: " + e.getMessage());
            for (Throwable s : e.getSuppressed()) System.out.println("  suppressed: " + s.getMessage());
        }

        System.out.println("3) the same with try/finally loses the original exception:");
        AuditChannel d = new AuditChannel("siem", true);
        try {
            try {
                d.send("");
            } finally {
                d.close();
            }
        } catch (RuntimeException e) {
            System.out.println("  caught: " + e.getMessage() + " (suppressed: " + e.getSuppressed().length + ")");
        }

        System.out.println("4) files in a temp dir, Java 9 form with an existing variable:");
        Path dir = Files.createTempDirectory("twr");
        Path file = dir.resolve("events.txt");
        try {
            BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
            try (writer) {
                writer.write("event-1\nevent-2\n");
            }
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                System.out.println("  read back: " + reader.lines().toList());
            }
            try {
                writer.write("too late");
            } catch (IOException e) {
                System.out.println("  writing after close -> " + e.getMessage());
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}

/* Output:
1) normal completion, closed in reverse order:
  open syslog
  open siem
  syslog <- login alice
  siem <- login alice
  close siem
  close syslog
2) body throws, close throws too:
  open siem
  close siem
  caught: empty event on siem
  suppressed: flush failed on siem
3) the same with try/finally loses the original exception:
  open siem
  close siem
  caught: flush failed on siem (suppressed: 0)
4) files in a temp dir, Java 9 form with an existing variable:
  read back: [event-1, event-2]
  writing after close -> Stream closed
*/
