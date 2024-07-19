// JEP 458 — Launch Multi-File Source-Code Programs (Java 22)
//
// Since Java 11 `java Hello.java` compiles and runs a single source file
// in memory, which is handy for scripts and for learning, but the moment a
// program outgrew one file you had to switch to javac, a class path and a
// build tool. Since Java 22 the launcher also finds the other classes the
// program uses in .java files next to it (following the usual package =
// directory layout) and compiles them on demand. This program writes a
// two-file program into a temporary directory and runs it with the same
// java executable that is running this file.
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class MultiFileSourcePrograms {
    static final String APP = """
            public class App {
                public static void main(String[] args) {
                    // Greeter lives in Greeter.java; no javac step needed
                    System.out.println(new Greeter("multi-file").greet());
                    System.out.println("same loader as App: " + (Greeter.class.getClassLoader() == App.class.getClassLoader()));
                }
            }
            """;

    static final String GREETER = """
            class Greeter {
                private final String name;

                Greeter(String name) {
                    this.name = name;
                }

                String greet() {
                    return "hello from a " + name + " program";
                }
            }
            """;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("jep458");
        try {
            Files.writeString(dir.resolve("App.java"), APP);
            Files.writeString(dir.resolve("Greeter.java"), GREETER);

            String java = ProcessHandle.current().info().command().orElse("java");
            Process p = new ProcessBuilder(List.of(java, "App.java"))
                    .directory(dir.toFile())
                    .redirectErrorStream(true)
                    .start();
            String out = new String(p.getInputStream().readAllBytes());
            System.out.println("$ java App.java   (directory holds App.java and Greeter.java)");
            System.out.print(out.indent(2));
            System.out.println("exit code " + p.waitFor());

            // Nothing was written to disk: no .class files next to the sources
            try (Stream<Path> files = Files.list(dir)) {
                System.out.println("files after the run: " + files.map(f -> f.getFileName().toString()).sorted().toList());
            }
        } finally {
            try (Stream<Path> files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
            }
        }
    }
}

/* Output:
$ java App.java   (directory holds App.java and Greeter.java)
  hello from a multi-file program
  same loader as App: true
exit code 0
files after the run: [App.java, Greeter.java]
*/
