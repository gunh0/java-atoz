// JEP 269 — Convenience Factory Methods for Collections (Java 9)
//
// Before Java 9, a small constant collection took several lines (or the
// double-brace initialization trick, which creates an anonymous subclass).
// List.of, Set.of and Map.of build compact, truly unmodifiable collections in
// one expression. They reject null elements, Set.of / Map.of reject
// duplicates at creation time, and the iteration order of Set.of / Map.of is
// deliberately unspecified (it changes between JVM runs) so nobody depends on it.
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class CollectionFactories {
    public static void main(String[] args) {
        // Before: copy into a mutable list, then wrap it
        List<String> oldList = Collections.unmodifiableList(new ArrayList<>(Arrays.asList("a", "b", "c")));
        Map<String, Integer> oldMap = new HashMap<>();
        oldMap.put("one", 1);
        oldMap.put("two", 2);
        oldMap = Collections.unmodifiableMap(oldMap);

        // After: one expression each
        List<String> list = List.of("a", "b", "c");
        Set<String> set = Set.of("red", "green", "blue");
        Map<String, Integer> map = Map.of("one", 1, "two", 2);
        // More than 10 pairs? Use Map.ofEntries
        Map<String, Integer> big = Map.ofEntries(Map.entry("three", 3), Map.entry("four", 4));

        System.out.println("list equals old list: " + list.equals(oldList));
        System.out.println("map equals old map:   " + map.equals(oldMap));
        System.out.println("set (sorted for display): " + new TreeSet<>(set));
        System.out.println("ofEntries (sorted):       " + new TreeMap<>(big));

        // Unmodifiable: every mutator throws
        attempt("list.add(\"d\")", () -> list.add("d"));
        attempt("map.put(\"x\", 0)", () -> map.put("x", 0));

        // Null-hostile, even for queries on Set/Map
        attempt("List.of(\"a\", null)", () -> List.of("a", null));
        attempt("set.contains(null)", () -> set.contains(null));

        // Duplicates are a programming error, detected immediately
        attempt("Set.of(\"x\", \"x\")", () -> Set.of("x", "x"));
        attempt("Map.of(\"k\", 1, \"k\", 2)", () -> Map.of("k", 1, "k", 2));

        // Collections.unmodifiableList is only a read-only view: changes to the
        // wrapped list show through. List.of has no mutable source behind it.
        List<String> source = new ArrayList<>(List.of("x"));
        List<String> view = Collections.unmodifiableList(source);
        source.add("y");
        System.out.println("unmodifiable view sees the change: " + view);

        // Map.entry also gives you a simple immutable pair
        Map.Entry<String, Integer> e = Map.entry("k", 1);
        System.out.println("entry: " + e + ", same as SimpleImmutableEntry: "
                + e.equals(new AbstractMap.SimpleImmutableEntry<>("k", 1)));
    }

    private static void attempt(String label, Runnable action) {
        try {
            action.run();
            System.out.println(label + " -> ok");
        } catch (RuntimeException ex) {
            System.out.println(label + " -> " + ex.getClass().getSimpleName());
        }
    }
}

/* Output:
list equals old list: true
map equals old map:   true
set (sorted for display): [blue, green, red]
ofEntries (sorted):       {four=4, three=3}
list.add("d") -> UnsupportedOperationException
map.put("x", 0) -> UnsupportedOperationException
List.of("a", null) -> NullPointerException
set.contains(null) -> NullPointerException
Set.of("x", "x") -> IllegalArgumentException
Map.of("k", 1, "k", 2) -> IllegalArgumentException
unmodifiable view sees the change: [x, y]
entry: k=1, same as SimpleImmutableEntry: true
*/
