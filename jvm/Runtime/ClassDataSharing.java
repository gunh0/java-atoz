// Class Data Sharing: loading classes from a memory-mapped archive
//
// Every JVM start parses, verifies and links the same few hundred JDK classes
// again. Class Data Sharing (CDS) does that once and stores the result in an
// archive that later JVMs memory-map: startup gets faster and, because the
// mapping is shared, several JVMs on one host share those pages. The JDK
// ships a default archive for its own classes (lib/server/classes.jsa), used
// unless -Xshare:off. Application CDS extends it to your classes: run once
// with -XX:ArchiveClassesAtExit=app.jsa (a "dynamic" archive layered on top
// of the default one), then start with -XX:SharedArchiveFile=app.jsa. Class
// path entries must be JAR files (a non-empty directory aborts the dump). The
// archive is only valid for the same JDK build and a matching class path;
// on a mismatch the JVM silently falls back to normal loading, unless
// -Xshare:on demands sharing, in which case it refuses to start.
// -Xlog:class+load prints each class's source ("shared objects file" for
// archived ones), which is how the program below tells, in child JVMs.
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

public class ClassDataSharing {
    static final String APP = """
            public class App {
                record Greeting(String text) {}
                public static void main(String[] args) {
                    System.out.println(new Greeting("hello from App").text());
                }
            }
            """;

    static String java;

    public static void main(String[] args) throws Exception {
        java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Path dir = Files.createTempDirectory("cds");
        try {
            Path src = dir.resolve("App.java");
            Files.writeString(src, APP);
            Path classes = Files.createDirectory(dir.resolve("classes"));
            StringWriter log = new StringWriter();
            if (ToolProvider.findFirst("javac").orElseThrow().run(new PrintWriter(log), new PrintWriter(log),
                    "-d", classes.toString(), src.toString()) != 0) throw new IllegalStateException(log.toString());
            Path jar = dir.resolve("app.jar");
            if (ToolProvider.findFirst("jar").orElseThrow().run(new PrintWriter(log), new PrintWriter(log),
                    "--create", "--file", jar.toString(), "-C", classes.toString(), ".") != 0) throw new IllegalStateException(log.toString());
            String cp = jar.toString();
            Path jsa = dir.resolve("app.jsa");

            Run plain = run("-Xlog:class+load", "-cp", cp, "App");
            System.out.println("default archive: java.lang.Object shared: " + plain.shared("java.lang.Object")
                    + ", App shared: " + plain.shared("App"));
            Run off = run("-Xshare:off", "-Xlog:class+load", "-cp", cp, "App");
            System.out.println("-Xshare:off:     java.lang.Object shared: " + off.shared("java.lang.Object"));

            Run dump = run("-XX:ArchiveClassesAtExit=" + jsa, "-cp", cp, "App");
            System.out.println("dump run: exit " + dump.exit + ", archive written: " + Files.exists(jsa));
            run("-XX:ArchiveClassesAtExit=" + dir.resolve("dir.jsa"), "-cp", classes.toString(), "App");
            System.out.println("dump with a class directory on the path: archive written: " + Files.exists(dir.resolve("dir.jsa")));

            Run withArchive = run("-XX:SharedArchiveFile=" + jsa, "-Xlog:class+load", "-cp", cp, "App");
            System.out.println("with app.jsa:    App shared: " + withArchive.shared("App") + ", App$Greeting shared: "
                    + withArchive.shared("App$Greeting") + ", output: " + withArchive.programOutput());

            // The archive records the class path; the same jar under another name does not match
            Path other = Files.copy(jar, dir.resolve("renamed.jar"));
            Run mismatch = run("-XX:SharedArchiveFile=" + jsa, "-Xlog:class+load", "-cp", other.toString(), "App");
            System.out.println("other class path: exit " + mismatch.exit + ", App shared: " + mismatch.shared("App"));
            Run strict = run("-Xshare:on", "-XX:SharedArchiveFile=" + jsa, "-cp", other.toString(), "App");
            System.out.println("same, with -Xshare:on: JVM started: " + (strict.exit == 0));
        } finally {
            try (Stream<Path> s = Files.walk(dir)) {
                for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
    }

    record Run(int exit, List<String> lines) {
        // "[info][class,load] java.lang.Object source: shared objects file"
        boolean shared(String className) {
            return lines.stream().anyMatch(l -> l.contains("[class,load] " + className + " source: shared objects file"));
        }

        String programOutput() {
            return lines.stream().filter(l -> !l.startsWith("[")).findFirst().orElse("");
        }
    }

    static Run run(String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of(java));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        List<String> lines = new String(p.getInputStream().readAllBytes()).lines().toList();
        return new Run(p.waitFor(), lines);
    }
}

/* Output:
default archive: java.lang.Object shared: true, App shared: false
-Xshare:off:     java.lang.Object shared: false
dump run: exit 0, archive written: true
dump with a class directory on the path: archive written: false
with app.jsa:    App shared: true, App$Greeting shared: true, output: hello from App
other class path: exit 0, App shared: false
same, with -Xshare:on: JVM started: false
*/
