// Open addressing stores entries directly in one array instead of in
// per-bucket lists. A key starts at slot hash(key) & (capacity - 1); on a
// collision, linear probing tries the next slot, then the next, until it
// finds the key or an empty slot. Lookups stop at the first empty slot, so
// remove() cannot simply empty a slot: that would cut the probe chain and
// make later keys unreachable. It leaves a TOMBSTONE instead, which lookups
// skip over and inserts may reuse. Tombstones count toward the load factor,
// and resizing (rehashing every live key) clears them out.
// Expected O(1) per operation while the table stays at most ~2/3 full.
// The hash is spread (h ^ h >>> 16, like HashMap) so keys that differ only
// in their high bits do not all land in the same low-bit slot.
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.TreeMap;

public class OpenAddressingHashMap {

    static final class OpenMap<K, V> {
        private static final Object TOMBSTONE = new Object();

        private Object[] keys = new Object[8];
        private Object[] values = new Object[8];
        private int size;   // live entries
        private int used;   // live entries + tombstones
        int probes;         // total slots inspected, for the demo

        int size() { return size; }
        int capacity() { return keys.length; }

        private static int spread(Object key) {
            int h = key.hashCode();
            return h ^ (h >>> 16);
        }

        // Returns the slot holding key, or -(slot + 1) of where it should be inserted
        private int find(Object key) {
            int mask = keys.length - 1;
            int firstTombstone = -1;
            for (int i = spread(key) & mask; ; i = (i + 1) & mask) {
                probes++;
                Object k = keys[i];
                if (k == null) {
                    return -((firstTombstone >= 0 ? firstTombstone : i) + 1);
                }
                if (k == TOMBSTONE) {
                    if (firstTombstone < 0) {
                        firstTombstone = i; // reuse it, but keep looking for the key
                    }
                } else if (k.equals(key)) {
                    return i;
                }
            }
        }

        @SuppressWarnings("unchecked")
        V get(K key) {
            int slot = find(Objects.requireNonNull(key));
            return slot >= 0 ? (V) values[slot] : null;
        }

        boolean containsKey(K key) {
            return find(Objects.requireNonNull(key)) >= 0;
        }

        @SuppressWarnings("unchecked")
        V put(K key, V value) {
            int slot = find(Objects.requireNonNull(key));
            if (slot >= 0) {
                V old = (V) values[slot];
                values[slot] = value;
                return old;
            }
            int target = -slot - 1;
            if (keys[target] == null) {
                used++; // a tombstone being reused was already counted
            }
            keys[target] = key;
            values[target] = value;
            size++;
            if (used * 3 >= keys.length * 2) { // load (incl. tombstones) >= 2/3
                resize(size * 3 >= keys.length ? keys.length * 2 : keys.length);
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        V remove(K key) {
            int slot = find(Objects.requireNonNull(key));
            if (slot < 0) {
                return null;
            }
            V old = (V) values[slot];
            keys[slot] = TOMBSTONE; // keep the probe chain intact
            values[slot] = null;
            size--;
            return old;
        }

        // Rehash live entries only; same capacity just purges tombstones
        @SuppressWarnings("unchecked")
        private void resize(int newCapacity) {
            Object[] oldKeys = keys;
            Object[] oldValues = values;
            keys = new Object[newCapacity];
            values = new Object[newCapacity];
            size = used = 0;
            for (int i = 0; i < oldKeys.length; i++) {
                if (oldKeys[i] != null && oldKeys[i] != TOMBSTONE) {
                    put((K) oldKeys[i], (V) oldValues[i]);
                }
            }
        }

        // Slot layout: key, "x" for a tombstone, "." for empty
        String layout() {
            StringBuilder sb = new StringBuilder();
            for (Object k : keys) {
                sb.append(k == null ? "." : k == TOMBSTONE ? "x" : k).append(' ');
            }
            return sb.toString().trim();
        }

        Map<K, V> toSortedMap() {
            Map<K, V> out = new TreeMap<>();
            for (int i = 0; i < keys.length; i++) {
                if (keys[i] != null && keys[i] != TOMBSTONE) {
                    @SuppressWarnings("unchecked") K k = (K) keys[i];
                    @SuppressWarnings("unchecked") V v = (V) values[i];
                    out.put(k, v);
                }
            }
            return out;
        }
    }

    public static void main(String[] args) {
        // Small Integer keys hash to themselves, so collisions are easy to see
        OpenMap<Integer, String> m = new OpenMap<>();
        m.put(1, "one");
        m.put(9, "nine");     // 9 & 7 == 1: collides with 1, probes to slot 2
        m.put(17, "seventeen"); // also slot 1: probes to slot 3
        System.out.println("after 1, 9, 17:   " + m.layout());
        m.remove(9);
        System.out.println("remove(9):        " + m.layout());
        // Without the tombstone, the search for 17 would stop at slot 2
        System.out.println("get(17) -> " + m.get(17) + ", get(9) -> " + m.get(9));
        m.put(25, "twenty-five"); // reuses the tombstone at slot 2
        System.out.println("put(25) reuses x: " + m.layout());
        System.out.println("put(1) again returns old value: " + m.put(1, "ONE") + ", size " + m.size());

        // Self-check: random puts/removes/gets against java.util.HashMap
        Random rnd = new Random(2024);
        OpenMap<String, Integer> mine = new OpenMap<>();
        Map<String, Integer> ref = new HashMap<>();
        boolean same = true;
        int ops = 200_000;
        for (int op = 0; op < ops; op++) {
            String key = "k" + rnd.nextInt(5_000);
            int v = rnd.nextInt();
            switch (rnd.nextInt(3)) {
                case 0 -> same &= Objects.equals(mine.put(key, v), ref.put(key, v));
                case 1 -> same &= Objects.equals(mine.remove(key), ref.remove(key));
                default -> same &= Objects.equals(mine.get(key), ref.get(key));
            }
        }
        same &= mine.toSortedMap().equals(new TreeMap<>(ref));
        System.out.println("200,000 random ops match HashMap: " + same + " (size " + mine.size()
                + ", capacity " + mine.capacity() + ")");
        System.out.printf(Locale.ROOT, "average probes per operation: %.2f%n", (double) mine.probes / ops);
    }
}

/* Output:
after 1, 9, 17:   . 1 9 17 . . . .
remove(9):        . 1 x 17 . . . .
get(17) -> seventeen, get(9) -> null
put(25) reuses x: . 1 25 17 . . . .
put(1) again returns old value: one, size 3
200,000 random ops match HashMap: true (size 2462, capacity 8192)
average probes per operation: 6.86
*/
