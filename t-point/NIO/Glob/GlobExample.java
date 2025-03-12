// Glob patterns select files by name. In Java they appear in
// FileSystem.getPathMatcher("glob:...") and Files.newDirectoryStream(dir, glob).
//   *        any characters within ONE name component (does not cross '/')
//   **       any characters across directory boundaries
//   ?        exactly one character
//   {a,b,c}  one of the alternatives (no nesting)
//   [abc] [a-z] [!0-9]   one character from / not from a set
//   \        escapes a special character, e.g. \* for a literal star
// Unlike a Unix shell, '*' also matches names starting with a dot.
// A matcher matches the WHOLE path it is given, so "*.log" never matches
// "logs/app.log"; match getFileName() or use "**.log" ("**/*.log" needs at
// least one directory, so it skips top-level files). "regex:..." takes a
// java.util.regex pattern instead. For conditions a name cannot express
// (size, type, age), DirectoryStream.Filter tests each entry, with the same
// one-directory, lazily-read, must-be-closed DirectoryStream.
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class GlobExample {
    static final List<String> NAMES = List.of(
            "app.log", "app.log.1", "app.log.2", "error.log", ".hidden.log", "notes.txt",
            "report-2024.csv", "report-2025.csv", "report-final.csv", "star*.txt",
            "logs/app.log", "logs/old/app.log", "src/Main.java", "src/util/Text.java");

    static void show(String pattern) {
        PathMatcher m = FileSystems.getDefault().getPathMatcher(pattern);
        List<String> hits = NAMES.stream().filter(n -> m.matches(Path.of(n))).toList();
        System.out.printf("%-36s %s%n", pattern, hits);
    }

    static List<String> list(Path dir, DirectoryStream.Filter<Path> filter) throws IOException {
        List<String> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, filter)) {
            ds.forEach(p -> out.add(p.getFileName().toString()));
        }
        out.sort(Comparator.naturalOrder()); // directory order is unspecified
        return out;
    }

    public static void main(String[] args) throws IOException {
        System.out.println("-- PathMatcher on relative paths");
        show("glob:*.log");
        show("glob:**.log");
        show("glob:logs/*");
        show("glob:logs/**");
        show("glob:app.log.?");
        show("glob:*.{csv,txt}");
        show("glob:report-[0-9][0-9][0-9][0-9].csv");
        show("glob:report-[!0-9]*.csv");
        show("glob:*\\**");
        show("glob:src/*.java");
        show("glob:src/**.java");
        show("regex:report-20\\d{2}\\.csv");

        Path dir = Files.createTempDirectory("glob");
        try {
            for (String name : NAMES) {
                Path p = dir.resolve(name);
                Files.createDirectories(p.getParent());
                Files.writeString(p, name.endsWith(".log") ? "x".repeat(200) : "x");
            }
            System.out.println("-- DirectoryStream (one directory, not recursive)");
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.log")) {
                List<String> names = new ArrayList<>();
                ds.forEach(p -> names.add(p.getFileName().toString()));
                names.sort(Comparator.naturalOrder());
                System.out.println("glob *.log:           " + names);
            }
            System.out.println("directories:          " + list(dir, Files::isDirectory));
            System.out.println("files over 100 bytes: " + list(dir, p -> Files.isRegularFile(p) && Files.size(p) > 100));
            PathMatcher rotated = FileSystems.getDefault().getPathMatcher("glob:*.log.[0-9]");
            System.out.println("rotated logs:         " + list(dir, p -> rotated.matches(p.getFileName())));
            System.out.println("matcher on the full path finds nothing: "
                    + list(dir, rotated::matches).isEmpty());

            System.out.println("-- recursive: Files.find with a ** matcher on the relative path");
            PathMatcher java = FileSystems.getDefault().getPathMatcher("glob:**.java");
            try (Stream<Path> s = Files.find(dir, Integer.MAX_VALUE, (p, attrs) -> java.matches(dir.relativize(p)))) {
                System.out.println("**.java: " + s.map(p -> dir.relativize(p).toString().replace('\\', '/')).sorted().toList());
            }
        } finally {
            try (Stream<Path> s = Files.walk(dir)) {
                for (Path p : s.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
        }
    }
}

/* Output:
-- PathMatcher on relative paths
glob:*.log                           [app.log, error.log, .hidden.log]
glob:**.log                          [app.log, error.log, .hidden.log, logs/app.log, logs/old/app.log]
glob:logs/*                          [logs/app.log]
glob:logs/**                         [logs/app.log, logs/old/app.log]
glob:app.log.?                       [app.log.1, app.log.2]
glob:*.{csv,txt}                     [notes.txt, report-2024.csv, report-2025.csv, report-final.csv, star*.txt]
glob:report-[0-9][0-9][0-9][0-9].csv [report-2024.csv, report-2025.csv]
glob:report-[!0-9]*.csv              [report-final.csv]
glob:*\**                            [star*.txt]
glob:src/*.java                      [src/Main.java]
glob:src/**.java                     [src/Main.java, src/util/Text.java]
regex:report-20\d{2}\.csv            [report-2024.csv, report-2025.csv]
-- DirectoryStream (one directory, not recursive)
glob *.log:           [.hidden.log, app.log, error.log]
directories:          [logs, src]
files over 100 bytes: [.hidden.log, app.log, error.log]
rotated logs:         [app.log.1, app.log.2]
matcher on the full path finds nothing: true
-- recursive: Files.find with a ** matcher on the relative path
**.java: [src/Main.java, src/util/Text.java]
*/
