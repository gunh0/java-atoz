// filter(predicate) keeps the elements that match; map(function) turns each
// element into another value. Both are intermediate operations and they are
// LAZY: nothing runs until a terminal operation (toList, count, forEach, ...)
// asks for elements. Elements then travel through the whole pipeline one at
// a time ("vertically"), not stage by stage, which is why short-circuiting
// terminals like findFirst() can stop after a few elements.
// peek() exists for debugging and shows that order here; do not use it for
// real side effects.
import java.util.List;
import java.util.stream.Stream;

public class FilterMapExample {
    record LogLine(String ip, String method, String path, int status) {
        static LogLine parse(String line) {
            String[] f = line.split(" ");
            return new LogLine(f[0], f[1], f[2], Integer.parseInt(f[3]));
        }
    }

    public static void main(String[] args) {
        List<String> raw = List.of(
                "10.0.0.5 GET /index.html 200",
                "203.0.113.9 POST /login 401",
                "203.0.113.9 POST /login 401",
                "10.0.0.7 GET /admin 403",
                "198.51.100.2 GET /health 200");

        // map: String -> LogLine, filter: keep failures, map: LogLine -> String
        List<String> failures = raw.stream()
                .map(LogLine::parse)
                .filter(l -> l.status() >= 400)
                .map(l -> l.ip() + " " + l.path() + " (" + l.status() + ")")
                .toList();
        System.out.println("failures: " + failures);

        // Several small filters read better than one big condition
        long externalLogins = raw.stream()
                .map(LogLine::parse)
                .filter(l -> !l.ip().startsWith("10."))
                .filter(l -> l.path().equals("/login"))
                .count();
        System.out.println("external /login attempts: " + externalLogins);

        // Laziness: building the pipeline prints nothing
        Stream<String> pipeline = raw.stream()
                .peek(s -> System.out.println("  read   " + s.substring(0, s.indexOf(' '))))
                .map(LogLine::parse)
                .filter(l -> l.status() == 403)
                .peek(l -> System.out.println("  match  " + l.path()))
                .map(LogLine::ip);
        System.out.println("pipeline built, nothing read yet");

        // findFirst pulls elements one by one and stops at the first match:
        // the last line is never read
        System.out.println("first 403 from: " + pipeline.findFirst().orElse("none"));
    }
}

/* Output:
failures: [203.0.113.9 /login (401), 203.0.113.9 /login (401), 10.0.0.7 /admin (403)]
external /login attempts: 2
pipeline built, nothing read yet
  read   10.0.0.5
  read   203.0.113.9
  read   203.0.113.9
  read   10.0.0.7
  match  /admin
first 403 from: 10.0.0.7
*/
