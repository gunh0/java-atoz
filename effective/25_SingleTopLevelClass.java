// Effective Java, Item 25: Limit source files to a single top-level class.
//
// The compiler allows several top-level classes in one source file, but then
// the class a program gets can depend on which files are passed to javac and
// whether javac finds them on its own. Below, Utensil.java and Dessert.java
// BOTH define classes Utensil and Dessert (with different contents).
// Depending on the command line, the same Main prints "pancake" or "potpie",
// or the build fails with a duplicate class. The fix is to keep each top-level class in its own file, or, if
// classes belong together, to make them static member classes of one class.
// This program drives javac through javax.tools in a temporary directory.
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class SingleTopLevelClass {
    static final String MAIN = """
            public class Main {
                public static String run() {
                    return Utensil.NAME + Dessert.NAME;
                }
            }
            """;
    static final String UTENSIL = """
            class Utensil {
                static final String NAME = "pan";
            }

            class Dessert {
                static final String NAME = "cake";
            }
            """;
    static final String DESSERT = """
            class Utensil {
                static final String NAME = "pot";
            }

            class Dessert {
                static final String NAME = "pie";
            }
            """;
    // Recommended: one file, one top-level class with static members
    static final String FIXED = """
            public class Main {
                private static class Utensil {
                    static final String NAME = "pan";
                }

                private static class Dessert {
                    static final String NAME = "cake";
                }

                public static String run() {
                    return Utensil.NAME + Dessert.NAME;
                }
            }
            """;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("item25");
        try {
            Path src = Files.createDirectories(dir.resolve("src"));
            Files.writeString(src.resolve("Main.java"), MAIN);
            Files.writeString(src.resolve("Utensil.java"), UTENSIL);
            Files.writeString(src.resolve("Dessert.java"), DESSERT);

            // javac looks up Utensil on the source path and finds Utensil.java
            build(dir, "javac Main.java", src, "Main.java");
            // Dessert.java is already on the command line, so its Utensil wins
            build(dir, "javac Main.java Dessert.java", src, "Main.java", "Dessert.java");
            // Both files on the command line: two definitions of each class
            build(dir, "javac Main.java Utensil.java Dessert.java", src, "Main.java", "Utensil.java", "Dessert.java");

            Path fixed = Files.createDirectories(dir.resolve("fixed"));
            Files.writeString(fixed.resolve("Main.java"), FIXED);
            build(dir, "javac Main.java (static member classes)", fixed, "Main.java");
        } finally {
            deleteRecursively(dir);
        }
    }

    // Compile the given files with -sourcepath src (as running javac in src
    // would) and, on success, load Main and call Main.run().
    static void build(Path dir, String label, Path src, String... files) throws Exception {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        Path out = Files.createTempDirectory(dir, "classes");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = javac.getStandardFileManager(diagnostics, null, null)) {
            List<Path> paths = new ArrayList<>();
            for (String f : files) {
                paths.add(src.resolve(f));
            }
            List<String> options = List.of("-sourcepath", src.toString(), "-d", out.toString(), "-implicit:class");
            boolean ok = javac.getTask(null, fm, diagnostics, options, null, fm.getJavaFileObjectsFromPaths(paths)).call();
            if (!ok) {
                for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                    if (d.getKind() == Diagnostic.Kind.ERROR) {
                        System.out.printf("%-45s -> compile error: %s%n", label, d.getMessage(Locale.ROOT));
                    }
                }
                return;
            }
        }
        try (URLClassLoader loader = new URLClassLoader(new URL[] {out.toUri().toURL()}, null)) {
            Object result = loader.loadClass("Main").getMethod("run").invoke(null);
            System.out.printf("%-45s -> %s%n", label, result);
        }
    }

    static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}

/* Output:
javac Main.java                               -> pancake
javac Main.java Dessert.java                  -> potpie
javac Main.java Utensil.java Dessert.java     -> compile error: duplicate class: Utensil
javac Main.java Utensil.java Dessert.java     -> compile error: duplicate class: Dessert
javac Main.java (static member classes)       -> pancake
*/
