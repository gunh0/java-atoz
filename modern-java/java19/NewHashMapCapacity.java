// Java 19 — HashMap.newHashMap and friends (API additions, no JEP)
//
// new HashMap<>(n) looks like "room for n mappings", but the int is the
// initial table CAPACITY. With the default load factor 0.75 the map resizes
// (rehashing every entry) once it holds more than 0.75 * capacity entries, so
// new HashMap<>(100) gets a 128-slot table that must grow when the 97th
// entry arrives. Getting it right meant writing (int) Math.ceil(n / 0.75)
// yourself. Java 19 adds static factories that take the expected number of
// MAPPINGS and do that calculation: HashMap.newHashMap, LinkedHashMap
// .newLinkedHashMap, WeakHashMap.newWeakHashMap, HashSet.newHashSet and
// LinkedHashSet.newLinkedHashSet.
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class NewHashMapCapacity {
    static final float LOAD_FACTOR = 0.75f;

    public static void main(String[] args) {
        int expected = 100;

        // Before: the constructor argument is a capacity, not a size
        Map<Integer, String> naive = new HashMap<>(expected);
        explain("new HashMap<>(100)", expected);

        // Before, done right: compute the capacity by hand
        Map<Integer, String> manual = new HashMap<>((int) Math.ceil(expected / (double) LOAD_FACTOR));
        explain("new HashMap<>(ceil(100 / 0.75))", (int) Math.ceil(expected / (double) LOAD_FACTOR));

        // After: say how many mappings you expect
        Map<Integer, String> sized = HashMap.newHashMap(expected);
        explain("HashMap.newHashMap(100)", (int) Math.ceil(expected / (double) LOAD_FACTOR));

        for (int i = 0; i < expected; i++) {
            naive.put(i, "v" + i);
            manual.put(i, "v" + i);
            sized.put(i, "v" + i);
        }
        System.out.println("all three hold " + naive.size() + " entries and are equal: "
                + (naive.equals(manual) && manual.equals(sized)));

        // The same factories exist for the other hash-based collections
        LinkedHashMap<String, Integer> ordered = LinkedHashMap.newLinkedHashMap(3);
        ordered.put("c", 3);
        ordered.put("a", 1);
        ordered.put("b", 2);
        Set<String> tags = HashSet.newHashSet(10);
        tags.add("java");
        LinkedHashSet<String> steps = LinkedHashSet.newLinkedHashSet(4);
        steps.add("build");
        steps.add("test");
        WeakHashMap<Object, String> cache = WeakHashMap.newWeakHashMap(16);
        System.out.println("newLinkedHashMap keeps insertion order: " + ordered);
        System.out.println("newHashSet: " + tags + ", newLinkedHashSet: " + steps + ", newWeakHashMap empty: " + cache.isEmpty());

        try {
            HashMap.newHashMap(-1);
        } catch (IllegalArgumentException e) {
            System.out.println("negative size: IllegalArgumentException");
        }
    }

    // HashMap rounds the requested capacity up to a power of two and resizes
    // when size exceeds capacity * loadFactor.
    static void explain(String label, int requestedCapacity) {
        int table = Integer.highestOneBit(Math.max(requestedCapacity - 1, 1)) << 1;
        int threshold = (int) (table * LOAD_FACTOR);
        System.out.printf("%-33s table %3d, resize after %3d entries -> %s for 100%n",
                label, table, threshold, threshold >= 100 ? "no resize" : "resizes");
    }
}

/* Output:
new HashMap<>(100)                table 128, resize after  96 entries -> resizes for 100
new HashMap<>(ceil(100 / 0.75))   table 256, resize after 192 entries -> no resize for 100
HashMap.newHashMap(100)           table 256, resize after 192 entries -> no resize for 100
all three hold 100 entries and are equal: true
newLinkedHashMap keeps insertion order: {c=3, a=1, b=2}
newHashSet: [java], newLinkedHashSet: [build, test], newWeakHashMap empty: true
negative size: IllegalArgumentException
*/
