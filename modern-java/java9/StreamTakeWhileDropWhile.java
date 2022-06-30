// Java 9 — Stream.takeWhile, dropWhile, iterate(seed, hasNext, next) and
// ofNullable (API additions, no JEP)
//
// Java 8 streams could filter elements but not stop at a condition: limit()
// only counts, and filter() keeps scanning to the end (forever, on an
// infinite stream). takeWhile keeps elements while a predicate holds and
// then stops; dropWhile skips them and passes the rest. The new
// three-argument iterate is the stream form of a for loop, and ofNullable
// makes a zero- or one-element stream from a possibly null value.
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class StreamTakeWhileDropWhile {
    public static void main(String[] args) {
        List<Integer> readings = List.of(3, 5, 8, 12, 4, 2, 15);

        // filter tests every element; takeWhile stops at the first failure
        System.out.println("filter(< 10):    " + readings.stream().filter(n -> n < 10).collect(Collectors.toList()));
        System.out.println("takeWhile(< 10): " + readings.stream().takeWhile(n -> n < 10).collect(Collectors.toList()));
        System.out.println("dropWhile(< 10): " + readings.stream().dropWhile(n -> n < 10).collect(Collectors.toList()));

        // Useful for skipping a header and stopping at a footer
        List<String> lines = List.of("# report", "# generated", "alpha=1", "beta=2", "END", "garbage");
        List<String> body = lines.stream()
                .dropWhile(l -> l.startsWith("#"))
                .takeWhile(l -> !l.equals("END"))
                .collect(Collectors.toList());
        System.out.println("body: " + body);

        // takeWhile terminates an infinite stream; filter would not
        System.out.println("powers of 2 below 1000: " + Stream.iterate(1, n -> n * 2)
                .takeWhile(n -> n < 1000).collect(Collectors.toList()));

        // Before: for (int i = 1; i <= 100; i *= 3)
        System.out.println("iterate with hasNext: " + IntStream.iterate(1, i -> i <= 100, i -> i * 3)
                .boxed().collect(Collectors.toList()));

        // ofNullable: no null checks inside flatMap
        Map<String, String> nicknames = Map.of("kim", "kimchi");
        List<String> found = Stream.of("kim", "lee")
                .flatMap(name -> Stream.ofNullable(nicknames.get(name)))
                .collect(Collectors.toList());
        System.out.println("ofNullable: " + found);
    }
}

/* Output:
filter(< 10):    [3, 5, 8, 4, 2]
takeWhile(< 10): [3, 5, 8]
dropWhile(< 10): [12, 4, 2, 15]
body: [alpha=1, beta=2]
powers of 2 below 1000: [1, 2, 4, 8, 16, 32, 64, 128, 256, 512]
iterate with hasNext: [1, 3, 9, 27, 81]
ofNullable: [kimchi]
*/
