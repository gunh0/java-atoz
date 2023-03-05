// A Stream<T> is a one-shot pipeline over a sequence of elements: a source,
// zero or more intermediate operations, and one terminal operation. It does
// not store elements and cannot be reused once consumed. Common sources:
//   collection.stream()            Arrays.stream(array[, from, to])
//   Stream.of(a, b, c)             Stream.ofNullable(x)   (0 or 1 element)
//   Stream.iterate(seed, next)     Stream.iterate(seed, hasNext, next)
//   Stream.generate(supplier)      infinite: always limit() it
//   Stream.builder(), Stream.concat(a, b), String.chars(), Pattern.splitAsStream
//   map.entrySet().stream(), BufferedReader.lines(), Files.lines(path)
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class StreamCreationExample {
    public static void main(String[] args) {
        List<String> admins = List.of("alice", "root");
        System.out.println("collection: " + admins.stream().toList());

        String[] ports = {"22", "80", "443", "8080"};
        System.out.println("array slice [1, 3): " + Arrays.stream(ports, 1, 3).toList());

        System.out.println("Stream.of: " + Stream.of("GET", "POST", "PUT").toList());
        String missing = null;
        System.out.println("Stream.ofNullable(null) count: " + Stream.ofNullable(missing).count());

        // iterate with a seed and a step; the 3-argument form has a stop condition
        System.out.println("powers of two: " + Stream.iterate(1, n -> n * 2).limit(8).toList());
        System.out.println("backoff ms <= 5000: " + Stream.iterate(100, ms -> ms <= 5000, ms -> ms * 3).toList());

        // generate is infinite: limit() is mandatory
        int[] counter = {0};
        System.out.println("generated ids: " + Stream.generate(() -> "req-" + (++counter[0])).limit(3).toList());

        Stream.Builder<String> builder = Stream.builder();
        builder.add("first").add("second");
        if (admins.contains("root")) builder.add("warn: root is admin");
        System.out.println("builder: " + builder.build().toList());

        System.out.println("concat: " + Stream.concat(Stream.of(1, 2), Stream.of(3)).toList());

        // Strings and regex
        System.out.println("chars of \"abc\": " + "abc".chars().boxed().toList());
        System.out.println("splitAsStream: " + Pattern.compile("\\s*,\\s*").splitAsStream("a , b,c").toList());
        System.out.println("lines(): " + "line1\nline2\r\nline3".lines().toList());

        // Maps are not collections, stream one of their views
        Map<String, Integer> hits = new TreeMap<>(Map.of("/login", 12, "/", 40));
        System.out.println("entries: " + hits.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList());

        // Primitive ranges
        System.out.println("IntStream.rangeClosed(1, 5): " + IntStream.rangeClosed(1, 5).boxed().toList());

        // A stream can only be consumed once
        Stream<String> once = admins.stream();
        once.count();
        try {
            once.count();
        } catch (IllegalStateException e) {
            System.out.println("reusing a stream -> IllegalStateException: " + e.getMessage());
        }
    }
}

/* Output:
collection: [alice, root]
array slice [1, 3): [80, 443]
Stream.of: [GET, POST, PUT]
Stream.ofNullable(null) count: 0
powers of two: [1, 2, 4, 8, 16, 32, 64, 128]
backoff ms <= 5000: [100, 300, 900, 2700]
generated ids: [req-1, req-2, req-3]
builder: [first, second, warn: root is admin]
concat: [1, 2, 3]
chars of "abc": [97, 98, 99]
splitAsStream: [a, b, c]
lines(): [line1, line2, line3]
entries: [/=40, /login=12]
IntStream.rangeClosed(1, 5): [1, 2, 3, 4, 5]
reusing a stream -> IllegalStateException: stream has already been operated upon or closed
*/
