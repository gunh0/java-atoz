// Effective Java, Item 46: Prefer side-effect-free functions in streams.
//
// Streams are a functional paradigm: each stage should be a pure function of
// its input. A pipeline whose forEach updates an external map is just an
// iterative loop in disguise, longer and harder to parallelise. forEach
// should only report the result of a computation. Do the actual work with
// collectors: toList/toSet/toCollection, toMap (with a merge function when
// keys may collide), groupingBy (with a downstream collector such as
// counting), and joining. Learn those and most pipelines become one
// expression without mutable state.
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SideEffectFreeStreams {
    static final String TEXT = "the cat and the hat and the bat sat on the mat with a cat";

    public static void main(String[] args) {
        // Bad: the stream API is used, but forEach mutates outside state
        Map<String, Long> freqBad = new HashMap<>();
        words().forEach(word -> freqBad.merge(word, 1L, Long::sum));
        System.out.println("forEach with side effects: " + new TreeMap<>(freqBad));

        // Good: the collector builds the map
        Map<String, Long> freq = words()
                .collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting()));
        System.out.println("groupingBy + counting:     " + freq);

        // Top three by frequency (ties broken alphabetically for a stable result)
        List<String> topThree = freq.keySet().stream()
                .sorted(Comparator.<String, Long>comparing(freq::get).reversed().thenComparing(Comparator.naturalOrder()))
                .limit(3)
                .toList();
        System.out.println("top three: " + topThree);

        // toMap with a merge function: longest word per first letter
        Map<Character, String> longestByLetter = words()
                .collect(Collectors.toMap(w -> w.charAt(0), w -> w,
                        (a, b) -> b.length() > a.length() ? b : a, TreeMap::new));
        System.out.println("toMap with merge: " + longestByLetter);

        // toMap without a merge function fails on duplicate keys
        try {
            words().collect(Collectors.toMap(w -> w.charAt(0), w -> w));
        } catch (IllegalStateException e) {
            System.out.println("toMap without merge: IllegalStateException");
        }

        // groupingBy into lists, then joining
        Map<Integer, String> byLength = words().distinct()
                .collect(Collectors.groupingBy(String::length, TreeMap::new, Collectors.joining(",", "[", "]")));
        System.out.println("by length, joined: " + byLength);
    }

    static Stream<String> words() {
        return Stream.of(TEXT.split(" "));
    }
}

/* Output:
forEach with side effects: {a=1, and=2, bat=1, cat=2, hat=1, mat=1, on=1, sat=1, the=4, with=1}
groupingBy + counting:     {a=1, and=2, bat=1, cat=2, hat=1, mat=1, on=1, sat=1, the=4, with=1}
top three: [the, and, cat]
toMap with merge: {a=and, b=bat, c=cat, h=hat, m=mat, o=on, s=sat, t=the, w=with}
toMap without merge: IllegalStateException
by length, joined: {1=[a], 2=[on], 3=[the,cat,and,hat,bat,sat,mat], 4=[with]}
*/
