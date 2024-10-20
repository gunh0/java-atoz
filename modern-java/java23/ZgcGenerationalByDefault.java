// output-jdk: 23 (GC modes and their flags change between releases)
// JEP 474 — ZGC: Generational Mode by Default (Java 23)
//
// Java 21 (JEP 439) gave ZGC a generational mode, but you had to ask for
// it with -XX:+UseZGC -XX:+ZGenerational; plain -XX:+UseZGC still meant
// the older single-generation collector. Generational ZGC turned out to be
// the better choice for almost all workloads (less CPU and heap headroom
// for the same sub-millisecond pauses), and maintaining two modes slows
// development. So in Java 23 -XX:+UseZGC selects the generational mode,
// and the ZGenerational option itself is deprecated: asking for the old
// mode with -XX:-ZGenerational still works but makes the JVM print
// deprecation warnings. This program starts child JVMs and lists the
// collector and heap-pool beans each one reports.
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ZgcGenerationalByDefault {
    static final String PROBE = """
            import java.lang.management.*;
            import java.util.stream.Collectors;

            public class Probe {
                public static void main(String[] args) {
                    System.out.println("collectors: " + ManagementFactory.getGarbageCollectorMXBeans().stream()
                            .map(GarbageCollectorMXBean::getName).sorted().collect(Collectors.joining(", ")));
                    System.out.println("heap pools: " + ManagementFactory.getMemoryPoolMXBeans().stream()
                            .filter(p -> p.getType() == MemoryType.HEAP)
                            .map(MemoryPoolMXBean::getName).sorted().collect(Collectors.joining(", ")));
                }
            }
            """;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("zgc23");
        Path probe = dir.resolve("Probe.java");
        Files.writeString(probe, PROBE);
        try {
            System.out.println("java -XX:+UseZGC (now generational by default)");
            run(dir, "-XX:+UseZGC");
            System.out.println("java -XX:+UseZGC -XX:-ZGenerational (old mode, deprecated)");
            run(dir, "-XX:+UseZGC", "-XX:-ZGenerational");
        } finally {
            Files.delete(probe);
            Files.delete(dir);
        }
    }

    static void run(Path dir, String... gcFlags) throws IOException, InterruptedException {
        String java = ProcessHandle.current().info().command().orElse("java");
        List<String> command = new ArrayList<>(List.of(java, "-Xmx128m"));
        command.addAll(List.of(gcFlags));
        command.add("Probe.java");
        Process p = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        List<String> lines = new String(p.getInputStream().readAllBytes()).lines().toList();
        // VM warnings are worded differently between builds: report them, don't quote them
        boolean warned = lines.stream().anyMatch(l -> l.contains("VM warning") && l.contains("deprecated"));
        lines.stream().filter(l -> !l.contains("VM warning")).forEach(l -> System.out.println("  " + l));
        System.out.println("  deprecation warning printed: " + warned + ", exit code " + p.waitFor());
    }
}

/* Output:
java -XX:+UseZGC (now generational by default)
  collectors: ZGC Major Cycles, ZGC Major Pauses, ZGC Minor Cycles, ZGC Minor Pauses
  heap pools: ZGC Old Generation, ZGC Young Generation
  deprecation warning printed: false, exit code 0
java -XX:+UseZGC -XX:-ZGenerational (old mode, deprecated)
  collectors: ZGC Cycles, ZGC Pauses
  heap pools: ZHeap
  deprecation warning printed: true, exit code 0
*/
