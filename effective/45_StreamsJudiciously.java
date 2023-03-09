// Effective Java, Item 45: Use streams judiciously.
//
// Streams shine at uniform transformations, filtering, combining with a
// single operation, collecting and searching. They are poor when the code
// must read or modify local variables, return or break early, throw checked
// exceptions, or keep several intermediate values around at once (the
// pipeline only remembers the current element). Overusing them produces
// pipelines that are hard to read; the best code often mixes a small helper
// method with a short pipeline. Name lambda parameters carefully (there are
// no explicit types to help the reader), and avoid streams over char values:
// "abc".chars() is an IntStream.
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StreamsJudiciously {
    static final List<String> WORDS = List.of("staple", "pastel", "angel", "glean", "petals",
            "inch", "chin", "listen", "silent", "enlist", "tinsel", "solo");

    public static void main(String[] args) {
        // 1. Iterative: clear, but computeIfAbsent is the only clever bit
        Map<String, Set<String>> groups = new TreeMap<>();
        for (String word : WORDS) {
            groups.computeIfAbsent(alphabetize(word), unused -> new TreeSet<>()).add(word);
        }
        for (Set<String> group : groups.values()) {
            if (group.size() >= 3) {
                System.out.println("iterative: " + group.size() + ": " + group);
            }
        }

        // 2. Overused: alphabetizing inside the pipeline, hard to follow
        WORDS.stream()
                .collect(Collectors.groupingBy(word -> word.chars().sorted()
                        .collect(StringBuilder::new, (sb, c) -> sb.append((char) c), StringBuilder::append)
                        .toString(), TreeMap::new, Collectors.toCollection(TreeSet::new)))
                .values().stream()
                .filter(group -> group.size() >= 3)
                .map(group -> group.size() + ": " + group)
                .forEach(g -> System.out.println("overused:  " + g));

        // 3. Balanced: a named helper plus a short pipeline
        WORDS.stream()
                .collect(Collectors.groupingBy(StreamsJudiciously::alphabetize, TreeMap::new,
                        Collectors.toCollection(TreeSet::new)))
                .values().stream()
                .filter(group -> group.size() >= 3)
                .forEach(g -> System.out.println("balanced:  " + g.size() + ": " + g));

        // char streams are IntStreams: prints code points, not letters
        StringBuilder printed = new StringBuilder();
        "Hi!".chars().forEach(printed::append);
        System.out.println("\"Hi!\".chars(): " + printed);
        printed.setLength(0);
        "Hi!".chars().forEach(c -> printed.append((char) c)); // a cast is needed
        System.out.println("with cast:     " + printed);

        // Cartesian product: both styles are fine; pick the one you find clearer
        List<String> loops = new ArrayList<>();
        for (String suit : List.of("S", "H")) {
            for (String rank : List.of("A", "K", "Q")) {
                loops.add(rank + suit);
            }
        }
        List<String> stream = Stream.of("S", "H")
                .flatMap(suit -> Stream.of("A", "K", "Q").map(rank -> rank + suit))
                .toList();
        System.out.println("loops:   " + loops);
        System.out.println("flatMap: " + stream);
    }

    private static String alphabetize(String s) {
        char[] a = s.toCharArray();
        Arrays.sort(a);
        return new String(a);
    }
}

/* Output:
iterative: 3: [pastel, petals, staple]
iterative: 4: [enlist, listen, silent, tinsel]
overused:  3: [pastel, petals, staple]
overused:  4: [enlist, listen, silent, tinsel]
balanced:  3: [pastel, petals, staple]
balanced:  4: [enlist, listen, silent, tinsel]
"Hi!".chars(): 7210533
with cast:     Hi!
loops:   [AS, KS, QS, AH, KH, QH]
flatMap: [AS, KS, QS, AH, KH, QH]
*/
