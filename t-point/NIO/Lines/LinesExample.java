// Files.lines(path) returns a lazy Stream<String>: lines are read from disk
// as the stream pulls them, so a multi-gigabyte log can be filtered without
// loading it into memory (unlike readAllLines). Rules:
//  - the stream holds an open file: always use try-with-resources
//  - it decodes as UTF-8 by default; a malformed byte surfaces as an
//    UncheckedIOException in the middle of the pipeline
//  - short-circuiting operations (findFirst, anyMatch, limit) stop reading early
// Files.newBufferedReader(path).lines() is the same thing via a Reader.
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class LinesExample {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("lines");
        Path log = dir.resolve("access.log");
        try {
            Files.write(log, List.of(
                    "10.0.0.5 GET /index.html 200",
                    "10.0.0.9 GET /admin 403",
                    "10.0.0.5 POST /login 200",
                    "10.0.0.9 GET /admin 403",
                    "10.0.0.7 GET /.env 404",
                    "10.0.0.9 GET /admin/../etc/passwd 400",
                    "10.0.0.5 GET /logout 200"));

            // Count lines
            try (Stream<String> lines = Files.lines(log)) {
                System.out.println("lines: " + lines.count());
            }

            // Error responses per client IP
            try (Stream<String> lines = Files.lines(log)) {
                Map<String, Long> errors = lines.map(l -> l.split(" "))
                        .filter(f -> Integer.parseInt(f[3]) >= 400)
                        .collect(Collectors.groupingBy(f -> f[0], TreeMap::new, Collectors.counting()));
                System.out.println("4xx per IP: " + errors);
            }

            // Short-circuit: stop at the first suspicious request
            try (Stream<String> lines = Files.lines(log)) {
                System.out.println("first traversal attempt: "
                        + lines.filter(l -> l.contains("..")).findFirst().orElse("none"));
            }

            // Line numbers: index into the list with an IntStream
            List<String> all = Files.readAllLines(log);
            System.out.println("403 at lines: " + IntStream.range(0, all.size())
                    .filter(i -> all.get(i).endsWith(" 403")).mapToObj(i -> i + 1).toList());

            // Malformed UTF-8 fails lazily, inside the stream
            Path broken = dir.resolve("broken.log");
            Files.write(broken, new byte[] {'o', 'k', '\n', (byte) 0xC3, '(', '\n'});
            try (Stream<String> lines = Files.lines(broken)) {
                lines.forEach(l -> System.out.println("read: " + l));
            } catch (UncheckedIOException e) {
                System.out.println("UncheckedIOException: " + e.getCause().getClass().getSimpleName());
            } finally {
                Files.delete(broken);
            }
        } finally {
            Files.deleteIfExists(log);
            Files.deleteIfExists(dir);
        }
    }
}

/* Output:
lines: 7
4xx per IP: {10.0.0.7=1, 10.0.0.9=3}
first traversal attempt: 10.0.0.9 GET /admin/../etc/passwd 400
403 at lines: [2, 4]
UncheckedIOException: MalformedInputException
*/
