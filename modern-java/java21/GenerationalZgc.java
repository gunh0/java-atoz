// output-jdk: 21 (GC modes and their flags change between releases)
// JEP 439 — Generational ZGC (Java 21)
//
// ZGC (production-ready since Java 15) keeps pause times well under a
// millisecond regardless of heap size, but it treated the heap as one
// space: every collection marked all live objects, old and young alike.
// Most objects die young, so for allocation-heavy applications that meant
// a lot of wasted marking, a need for more heap headroom, and more CPU.
// Java 21 adds a generational mode: young and old generations are
// collected separately (frequent cheap minor collections, rarer major
// ones) while keeping ZGC's concurrent, low-pause design. It is enabled
// with -XX:+UseZGC -XX:+ZGenerational. There is no API to call, so this
// program launches two child JVMs, one per mode, and shows how the
// collector's structure differs through the standard management beans.
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GenerationalZgc {
    // Runs in the child JVM: churn through short-lived garbage, then report
    static final String PROBE = """
            import java.lang.management.*;
            import java.util.stream.Collectors;

            public class Probe {
                static volatile Object sink;

                public static void main(String[] args) {
                    for (int i = 0; i < 200_000; i++) {
                        sink = new byte[4096];          // ~800 MB of short-lived objects
                    }
                    System.gc();
                    for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                        System.out.println("  collector " + gc.getName() + ", ran: " + (gc.getCollectionCount() > 0));
                    }
                    System.out.println("  heap pools: " + ManagementFactory.getMemoryPoolMXBeans().stream()
                            .filter(p -> p.getType() == MemoryType.HEAP)
                            .map(MemoryPoolMXBean::getName)
                            .collect(Collectors.joining(", ")));
                }
            }
            """;

    public static void main(String[] args) throws Exception {
        // This JVM's own collector is whatever the launcher picked; we only use children
        Path dir = Files.createTempDirectory("zgc");
        Path probe = dir.resolve("Probe.java");
        Files.writeString(probe, PROBE);
        try {
            System.out.println("before: -XX:+UseZGC (single generation)");
            run(dir, "-XX:+UseZGC");
            System.out.println("after: -XX:+UseZGC -XX:+ZGenerational");
            run(dir, "-XX:+UseZGC", "-XX:+ZGenerational");
        } finally {
            Files.delete(probe);
            Files.delete(dir);
        }
    }

    static void run(Path dir, String... gcFlags) throws IOException, InterruptedException {
        String java = ProcessHandle.current().info().command().orElse("java");
        List<String> command = new ArrayList<>(List.of(java, "-Xmx256m"));
        command.addAll(List.of(gcFlags));
        command.add("Probe.java");
        Process p = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        System.out.print(new String(p.getInputStream().readAllBytes()));
        System.out.println("  exit code " + p.waitFor());
    }
}

/* Output:
before: -XX:+UseZGC (single generation)
  collector ZGC Cycles, ran: true
  collector ZGC Pauses, ran: true
  heap pools: ZHeap
  exit code 0
after: -XX:+UseZGC -XX:+ZGenerational
  collector ZGC Minor Cycles, ran: true
  collector ZGC Minor Pauses, ran: true
  collector ZGC Major Cycles, ran: true
  collector ZGC Major Pauses, ran: true
  heap pools: ZGC Old Generation, ZGC Young Generation
  exit code 0
*/
