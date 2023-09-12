// Traversing a directory tree with NIO.2:
//  - Files.list(dir): the direct children, as a Stream<Path>
//  - Files.walk(dir[, maxDepth]): the whole tree, depth first, dir included
//  - Files.find(dir, maxDepth, (path, attrs) -> ...): walk with a filter that
//    also gets the file's attributes (size, type) without another syscall
//  - Files.newDirectoryStream(dir, "*.{log,txt}"): one directory, glob filter
//  - FileSystems.getDefault().getPathMatcher("glob:**/*.java") for globs on
//    any path
// The streams hold open directory handles: use them in try-with-resources.
// Traversal order is not specified, so sort before printing.
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

public class WalkFindExample {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("walk");
        try {
            // A small project tree
            for (String f : List.of("src/Main.java", "src/util/Strings.java", "config/app.yml",
                    "config/secrets.env", "logs/app.log", "logs/old/app-2023-08.log", "README.txt")) {
                Path p = root.resolve(f);
                Files.createDirectories(p.getParent());
                Files.writeString(p, f.endsWith(".log") ? "x".repeat(2000) : "content of " + f);
            }

            try (Stream<Path> children = Files.list(root)) {
                System.out.println("list: " + children.map(p -> root.relativize(p).toString()).sorted().toList());
            }

            try (Stream<Path> all = Files.walk(root)) {
                System.out.println("walk (files only):");
                all.filter(Files::isRegularFile).map(root::relativize).map(Path::toString).sorted()
                        .forEach(p -> System.out.println("  " + p));
            }

            try (Stream<Path> shallow = Files.walk(root, 1)) {
                System.out.println("walk depth 1 count (incl. root): " + shallow.count());
            }

            // find: large files, using the attributes the walk already has
            try (Stream<Path> big = Files.find(root, Integer.MAX_VALUE,
                    (p, attrs) -> attrs.isRegularFile() && attrs.size() > 1000)) {
                System.out.println("find size > 1000: " + big.map(p -> root.relativize(p).toString()).sorted().toList());
            }

            // A path matcher with a glob: possible secrets anywhere in the tree
            PathMatcher secrets = FileSystems.getDefault().getPathMatcher("glob:**/*.{env,pem,key}");
            try (Stream<Path> found = Files.find(root, Integer.MAX_VALUE, (p, a) -> secrets.matches(p))) {
                System.out.println("secret-looking files (.env/.pem/.key): " + found.map(p -> root.relativize(p).toString()).toList());
            }

            // DirectoryStream with a glob: one directory only
            List<String> logs = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(root.resolve("logs"), "*.log")) {
                ds.forEach(p -> logs.add(p.getFileName().toString()));
            }
            System.out.println("logs/*.log (not recursive): " + logs);

            // Recursive delete: walk, deepest paths first
            try (Stream<Path> all = Files.walk(root)) {
                for (Path p : all.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
            System.out.println("tree deleted: " + !Files.exists(root));
        } finally {
            if (Files.exists(root)) {
                try (Stream<Path> all = Files.walk(root)) {
                    for (Path p : all.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
                }
            }
        }
    }
}

/* Output:
list: [README.txt, config, logs, src]
walk (files only):
  README.txt
  config/app.yml
  config/secrets.env
  logs/app.log
  logs/old/app-2023-08.log
  src/Main.java
  src/util/Strings.java
walk depth 1 count (incl. root): 5
find size > 1000: [logs/app.log, logs/old/app-2023-08.log]
secret-looking files (.env/.pem/.key): [config/secrets.env]
logs/*.log (not recursive): [app.log]
tree deleted: true
*/
