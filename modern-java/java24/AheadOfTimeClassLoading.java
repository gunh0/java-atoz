// output-jdk: 24
// JEP 483 — Ahead-of-Time Class Loading & Linking (Java 24)
//
// A Java application spends much of its startup reading, parsing, verifying
// and linking the same classes on every launch, and frameworks that scan
// annotations or spin up proxies make it worse. Class Data Sharing
// (jvm/Runtime/ClassDataSharing.java) already archived parsed classes; Java
// 24 goes further and caches them loaded AND linked, as observed in a
// training run, in an "AOT cache". It is the first step of Project Leyden and
// needs no code changes, only a three-step workflow:
//   1. java -XX:AOTMode=record -XX:AOTConfiguration=app.aotconf -cp app.jar App   (training run)
//   2. java -XX:AOTMode=create -XX:AOTConfiguration=app.aotconf -XX:AOTCache=app.aot -cp app.jar
//   3. java -XX:AOTCache=app.aot -cp app.jar App                                  (every later run)
// The cache is valid only for the same JDK build, OS/CPU and a class path
// that matches the training run (JAR files only). If it cannot be used the
// JVM silently starts without it, unless -XX:AOTMode=on demands it. The
// program runs all three steps in child JVMs inside a temporary directory
// and checks, via -Xlog:class+load, where the application's classes come
// from. Startup times are not printed: they depend on the machine. Output
// is pinned to JDK 24, the release whose flags and workflow it shows.
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

public class AheadOfTimeClassLoading {
    static final String APP = """
            import java.util.*;
            import java.util.stream.*;
            public class App {
                record Order(String item, int qty) {}
                public static void main(String[] args) {
                    var orders = List.of(new Order("tea", 2), new Order("cake", 1), new Order("tea", 3));
                    Map<String, Integer> totals = orders.stream()
                            .collect(Collectors.groupingBy(Order::item, TreeMap::new, Collectors.summingInt(Order::qty)));
                    System.out.println("App says " + totals);
                }
            }
            """;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("aot");
        try {
            Path src = dir.resolve("App.java");
            Files.writeString(src, APP);
            Path classes = Files.createDirectory(dir.resolve("classes"));
            tool("javac", "-d", classes.toString(), src.toString());
            Path jar = dir.resolve("app.jar");
            tool("jar", "--create", "--file", jar.toString(), "-C", classes.toString(), ".");
            String cp = jar.toString();
            Path conf = dir.resolve("app.aotconf"), cache = dir.resolve("app.aot");

            Run record = java("-XX:AOTMode=record", "-XX:AOTConfiguration=" + conf, "-cp", cp, "App");
            System.out.println("1. training run:   exit " + record.exit + ", " + record.output() + ", configuration written: " + Files.exists(conf));
            Run create = java("-XX:AOTMode=create", "-XX:AOTConfiguration=" + conf, "-XX:AOTCache=" + cache, "-cp", cp);
            System.out.println("2. create cache:   exit " + create.exit + ", cache written: " + Files.exists(cache));

            Run cold = java("-Xlog:class+load", "-cp", cp, "App");
            Run warm = java("-XX:AOTCache=" + cache, "-Xlog:class+load", "-cp", cp, "App");
            System.out.println("3. without cache:  App$Order from the cache: " + cold.fromCache("App$Order") + ", " + cold.output());
            System.out.println("   with cache:     App$Order from the cache: " + warm.fromCache("App$Order") + ", " + warm.output());
            // Without the AOT cache the JVM still maps the JDK's default CDS archive; the cache adds the rest
            System.out.println("   classes not from a cache: fewer with the AOT cache: " + (warm.notCached() < cold.notCached()));

            // A class path that does not match the training run
            Path renamed = Files.copy(jar, dir.resolve("renamed.jar"));
            Run fallback = java("-XX:AOTCache=" + cache, "-Xlog:class+load", "-cp", renamed.toString(), "App");
            System.out.println("other class path:  exit " + fallback.exit + ", App$Order from the cache: " + fallback.fromCache("App$Order"));
            Run strict = java("-XX:AOTMode=on", "-XX:AOTCache=" + cache, "-cp", renamed.toString(), "App");
            System.out.println("same with -XX:AOTMode=on: JVM starts: " + (strict.exit == 0));
        } finally {
            try (Stream<Path> s = Files.walk(dir)) {
                for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
    }

    record Run(int exit, List<String> lines) {
        boolean fromCache(String cls) {
            return lines.stream().anyMatch(l -> l.contains("[class,load] " + cls + " source: shared objects file"));
        }

        long notCached() {
            return lines.stream().filter(l -> l.contains("[class,load]") && !l.contains("shared objects file")).count();
        }

        String output() {
            return lines.stream().filter(l -> l.startsWith("App says")).findFirst().orElse("(no output)");
        }
    }

    static Run java(String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString()));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        List<String> lines = new String(p.getInputStream().readAllBytes()).lines().toList();
        return new Run(p.waitFor(), lines);
    }

    static void tool(String name, String... args) {
        StringWriter out = new StringWriter();
        if (ToolProvider.findFirst(name).orElseThrow().run(new PrintWriter(out), new PrintWriter(out), args) != 0) {
            throw new IllegalStateException(name + " failed:\n" + out);
        }
    }
}

/* Output:
1. training run:   exit 0, App says {cake=1, tea=5}, configuration written: true
2. create cache:   exit 0, cache written: true
3. without cache:  App$Order from the cache: false, App says {cake=1, tea=5}
   with cache:     App$Order from the cache: true, App says {cake=1, tea=5}
   classes not from a cache: fewer with the AOT cache: true
other class path:  exit 0, App$Order from the cache: false
same with -XX:AOTMode=on: JVM starts: false
*/
