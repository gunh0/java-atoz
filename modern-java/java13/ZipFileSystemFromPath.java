// Java 13 — FileSystems.newFileSystem(Path) and newFileSystem(Path, Map)
// (API additions, no JEP)
//
// The zip file system provider lets the ordinary NIO.2 Files API read and
// write entries inside a zip/jar as if it were a directory tree. Opening
// one used to require building a "jar:file:..." URI by hand (with escaping
// pitfalls) to pass an env map such as create=true, or calling
// newFileSystem(path, (ClassLoader) null). Java 13 added overloads that
// take the Path directly, with or without an env map. (Side effect worth
// knowing: newFileSystem(path, null) became ambiguous at compile time.)
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ZipFileSystemFromPath {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("zipfs");
        try {
            Path oldZip = dir.resolve("old.zip");
            Path zip = dir.resolve("report.zip");

            // Before: build a jar: URI from the path to pass the env map
            URI uri = URI.create("jar:" + oldZip.toUri());
            try (FileSystem fs = FileSystems.newFileSystem(uri, Map.of("create", "true"))) {
                Files.writeString(fs.getPath("/legacy.txt"), "made via a jar: URI");
            }

            // After: pass the Path and the env map
            try (FileSystem fs = FileSystems.newFileSystem(zip, Map.of("create", "true"))) {
                Path logs = Files.createDirectories(fs.getPath("/logs"));
                Files.writeString(logs.resolve("app.log"), "INFO started\nWARN disk 91%\n");
                Files.writeString(fs.getPath("/summary.txt"), "1 warning", StandardCharsets.UTF_8);
                // the usual Files operations (copy, move, delete ...) work inside the zip
                Files.copy(fs.getPath("/summary.txt"), fs.getPath("/logs/summary-copy.txt"));
            } // closing the file system writes the zip

            // Reopen the existing zip with just the Path
            try (FileSystem fs = FileSystems.newFileSystem(zip)) {
                List<String> entries;
                try (Stream<Path> walk = Files.walk(fs.getPath("/"))) {
                    entries = walk.filter(Files::isRegularFile)
                            .map(Path::toString)
                            .sorted(Comparator.naturalOrder())
                            .collect(Collectors.toList());
                }
                System.out.println("entries: " + entries);
                System.out.println("warnings: " + Files.readAllLines(fs.getPath("/logs/app.log")).stream()
                        .filter(l -> l.startsWith("WARN")).collect(Collectors.toList()));
                System.out.println("provider scheme: " + fs.provider().getScheme());
            }

            try (FileSystem fs = FileSystems.newFileSystem(oldZip)) {
                System.out.println("old zip: " + Files.readString(fs.getPath("/legacy.txt")));
            }

            // newFileSystem(zip, null) no longer compiles (ambiguous): pick an overload explicitly
            try (FileSystem fs = FileSystems.newFileSystem(zip, (ClassLoader) null)) {
                System.out.println("explicit overload works: " + Files.exists(fs.getPath("/summary.txt")));
            }
        } finally {
            try (Stream<Path> walk = Files.walk(dir)) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
                    Files.delete(p);
                }
            }
        }
    }
}

/* Output:
entries: [/logs/app.log, /logs/summary-copy.txt, /summary.txt]
warnings: [WARN disk 91%]
provider scheme: jar
old zip: made via a jar: URI
explicit overload works: true
*/
