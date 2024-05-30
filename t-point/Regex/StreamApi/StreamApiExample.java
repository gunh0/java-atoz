// java.util.regex plugs into the Stream API:
//   Pattern.splitAsStream(input)  lazy split, same rules as split() (trailing
//                                 empty strings are dropped)
//   Pattern.asPredicate()         true if the pattern is *found* in a string
//   Pattern.asMatchPredicate()    true if the *whole* string matches (Java 11)
//   Matcher.results()             a Stream<MatchResult> of all matches (Java 9)
// String.split(regex, limit) controls the trailing-empty behaviour: limit 0
// (the default) drops trailing empty strings, a negative limit keeps them,
// and a positive limit caps the number of pieces.
import java.util.Arrays;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StreamApiExample {
    public static void main(String[] args) {
        Pattern comma = Pattern.compile("\\s*,\\s*");
        String csv = "alpha , beta,gamma,, delta,,";

        System.out.println("split:          " + Arrays.toString(comma.split(csv)));
        System.out.println("split limit -1: " + Arrays.toString(csv.split("\\s*,\\s*", -1)));
        System.out.println("split limit 2:  " + Arrays.toString(csv.split("\\s*,\\s*", 2)));
        System.out.println("splitAsStream:  " + comma.splitAsStream(csv)
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase)
                .toList());

        // A zero-width match at the start does not produce a leading empty string
        System.out.println("split \"\" on \"abc\": " + Arrays.toString("abc".split("")));

        // find vs matches as predicates
        List<String> inputs = List.of("10.0.0.1", "host 10.0.0.1", "10.0.0", "256.1.1.1");
        Pattern ipv4 = Pattern.compile("\\d{1,3}(?:\\.\\d{1,3}){3}");
        System.out.println("asPredicate:      " + inputs.stream().filter(ipv4.asPredicate()).toList());
        System.out.println("asMatchPredicate: " + inputs.stream().filter(ipv4.asMatchPredicate()).toList());

        // results(): every match as a MatchResult
        String log = "GET /a 200 12ms; GET /b 404 3ms; POST /c 500 120ms";
        Pattern entry = Pattern.compile("(\\w+) (/\\w+) (\\d{3}) (\\d+)ms");
        int totalMs = entry.matcher(log).results().mapToInt(r -> Integer.parseInt(r.group(4))).sum();
        System.out.println("total time: " + totalMs + "ms");
        String failing = entry.matcher(log).results()
                .filter(r -> r.group(3).startsWith("5") || r.group(3).startsWith("4"))
                .map(r -> r.group(2) + "=" + r.group(3))
                .collect(Collectors.joining(", "));
        System.out.println("failing: " + failing);

        List<String> positions = Pattern.compile("ms").matcher(log).results()
                .map(MatchResult::start)
                .map(String::valueOf)
                .toList();
        System.out.println("'ms' at: " + positions);

        // Word frequencies with splitAsStream
        String text = "the quick brown fox jumps over the lazy dog the end";
        System.out.println("word counts: " + Pattern.compile("\\s+").splitAsStream(text)
                .collect(Collectors.groupingBy(w -> w, java.util.TreeMap::new, Collectors.counting())));
    }
}

/* Output:
split:          [alpha, beta, gamma, , delta]
split limit -1: [alpha, beta, gamma, , delta, , ]
split limit 2:  [alpha, beta,gamma,, delta,,]
splitAsStream:  [ALPHA, BETA, GAMMA, DELTA]
split "" on "abc": [a, b, c]
asPredicate:      [10.0.0.1, host 10.0.0.1, 256.1.1.1]
asMatchPredicate: [10.0.0.1, 256.1.1.1]
total time: 135ms
failing: /b=404, /c=500
'ms' at: [13, 29, 48]
word counts: {brown=1, dog=1, end=1, fox=1, jumps=1, lazy=1, over=1, quick=1, the=3}
*/
