// Safepoints, handshakes, and why biased locking was removed
//
// Some VM work needs every Java thread stopped at a known place, where the
// JVM can find all object references in frames and registers: a safepoint.
// Compiled code polls a safepoint flag at method returns and loop back-edges;
// a "stop-the-world" VM operation (most GC phases, deoptimization, class
// redefinition, many diagnostic commands) sets it and waits for every thread
// to arrive. Time-to-safepoint is a classic latency source: a long counted
// loop without polls once stalled everyone (loop strip mining, Java 10,
// inserts polls). Thread-local handshakes (JEP 312, Java 10) let the VM stop
// ONE thread instead of all of them.
// Biased locking (since Java 6) made uncontended synchronized nearly free by
// "biasing" an object to the first thread that locked it, but revoking a bias
// needed a safepoint, and the code was a maintenance burden; modern code
// rarely uses synchronized collections in hot paths. JEP 374 disabled and
// deprecated it in Java 15, it was removed in Java 18, and the flags are now
// unknown to the JVM. Below: a child JVM asked for biased locking refuses to
// start, and JFR shows the safepoint behind a System.gc() in this process.
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

public class SafepointsAndBiasedLocking {
    public static void main(String[] args) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        for (String flag : List.of("-XX:+UseBiasedLocking", "-XX:BiasedLockingStartupDelay=0", "-XX:-UseBiasedLocking")) {
            Process p = new ProcessBuilder(java, flag, "-version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            System.out.printf("java %-32s starts: %s%n", flag, p.waitFor() == 0);
        }
        Process ok = new ProcessBuilder(java, "-version").redirectErrorStream(true).start();
        ok.getInputStream().readAllBytes();
        System.out.printf("java %-32s starts: %s%n", "(no flags)", ok.waitFor() == 0);

        // Safepoints in this JVM, recorded by JFR around an explicit full GC
        Path file = Files.createTempFile("safepoints", ".jfr");
        try (Recording r = new Recording()) {
            r.enable("jdk.SafepointBegin");
            r.enable("jdk.ExecuteVMOperation");
            r.start();
            System.gc();
            r.stop();
            r.dump(file);
            List<RecordedEvent> events = RecordingFile.readAllEvents(file);
            long safepoints = events.stream().filter(e -> e.getEventType().getName().equals("jdk.SafepointBegin")).count();
            boolean opAtSafepoint = events.stream()
                    .filter(e -> e.getEventType().getName().equals("jdk.ExecuteVMOperation"))
                    .anyMatch(e -> e.getBoolean("safepoint"));
            System.out.println("System.gc() reached a safepoint:           " + (safepoints > 0));
            System.out.println("a VM operation executed at that safepoint: " + opAtSafepoint);
        } finally {
            Files.deleteIfExists(file);
        }

        // Locking still works, of course: it just never biases any more
        Object lock = new Object();
        int n = 0;
        for (int i = 0; i < 1_000; i++) synchronized (lock) { n++; }
        System.out.println("uncontended synchronized, 1000 times: " + n);
    }
}

/* Output:
java -XX:+UseBiasedLocking            starts: false
java -XX:BiasedLockingStartupDelay=0  starts: false
java -XX:-UseBiasedLocking            starts: false
java (no flags)                       starts: true
System.gc() reached a safepoint:           true
a VM operation executed at that safepoint: true
uncontended synchronized, 1000 times: 1000
*/
