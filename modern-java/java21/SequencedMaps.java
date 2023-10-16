// JEP 431 — Sequenced Collections, part 2: SequencedMap (Java 21)
//
// LinkedHashMap keeps insertion (or access) order and is the usual choice
// for small LRU caches and ordered JSON-like data, yet it offered no way to
// look at its oldest or newest entry, to insert at the front, or to walk it
// backwards; only SortedMap/NavigableMap had firstKey()/lastEntry()/
// descendingMap(). Java 21 adds the SequencedMap interface, implemented by
// LinkedHashMap and SortedMap: firstEntry/lastEntry, pollFirstEntry/
// pollLastEntry, putFirst/putLast, reversed(), and sequenced views
// sequencedKeySet/sequencedValues/sequencedEntrySet.
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;
import java.util.TreeMap;

public class SequencedMaps {
    public static void main(String[] args) {
        LinkedHashMap<String, Integer> sessions = new LinkedHashMap<>();
        sessions.put("alice", 1);
        sessions.put("bob", 2);
        sessions.put("carol", 3);

        // Before: oldest = first iterator entry, newest = iterate to the end
        Map.Entry<String, Integer> oldest = sessions.entrySet().iterator().next();
        Map.Entry<String, Integer> newest = null;
        for (Map.Entry<String, Integer> e : sessions.entrySet()) {
            newest = e;
        }
        System.out.println("before: oldest " + oldest + ", newest " + newest);

        // After: direct access at both ends
        System.out.println("after:  oldest " + sessions.firstEntry() + ", newest " + sessions.lastEntry());

        // putFirst / putLast reposition (insertion-ordered maps only)
        sessions.putFirst("root", 0);
        sessions.putLast("alice", 9);  // existing key: value updated AND moved to the end
        System.out.println("putFirst(root), putLast(alice): " + sessions);

        // Reverse views and sequenced key/value views
        System.out.println("reversed: " + sessions.reversed());
        System.out.println("keys newest first: " + sessions.sequencedKeySet().reversed());
        System.out.println("last value: " + sessions.sequencedValues().getLast());

        // pollFirstEntry: evict the oldest entry, e.g. in a bounded cache
        Map.Entry<String, Integer> evicted = sessions.pollFirstEntry();
        System.out.println("evicted " + evicted + ", left " + sessions);

        // Entries returned by firstEntry()/lastEntry() are snapshots and cannot be modified
        try {
            sessions.firstEntry().setValue(100);
        } catch (UnsupportedOperationException e) {
            System.out.println("firstEntry().setValue: UnsupportedOperationException");
        }

        // The same methods on a sorted map, which already had most of them under other names
        SequencedMap<Integer, String> ranks = new TreeMap<>(Map.of(3, "bronze", 1, "gold", 2, "silver"));
        System.out.println("TreeMap first " + ranks.firstEntry() + ", last " + ranks.lastEntry()
                + ", reversed " + ranks.reversed());
        try {
            ranks.putFirst(0, "platinum"); // a sorted map decides the order itself
        } catch (UnsupportedOperationException e) {
            System.out.println("TreeMap.putFirst: UnsupportedOperationException");
        }

        // Generic code over any ordered map
        System.out.println("newest key: " + newestKey(sessions) + ", " + newestKey(ranks));
        SequencedMap<String, Integer> readOnly = Collections.unmodifiableSequencedMap(sessions);
        Iterator<String> it = readOnly.reversed().keySet().iterator();
        System.out.println("unmodifiable reversed iteration: " + it.next() + ", " + it.next());
    }

    static <K> K newestKey(SequencedMap<K, ?> map) {
        return map.lastEntry().getKey();
    }
}

/* Output:
before: oldest alice=1, newest carol=3
after:  oldest alice=1, newest carol=3
putFirst(root), putLast(alice): {root=0, bob=2, carol=3, alice=9}
reversed: {alice=9, carol=3, bob=2, root=0}
keys newest first: [alice, carol, bob, root]
last value: 9
evicted root=0, left {bob=2, carol=3, alice=9}
firstEntry().setValue: UnsupportedOperationException
TreeMap first 1=gold, last 3=bronze, reversed {3=bronze, 2=silver, 1=gold}
TreeMap.putFirst: UnsupportedOperationException
newest key: alice, 3
unmodifiable reversed iteration: alice, carol
*/
