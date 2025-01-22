// Consistent hashing spreads keys over servers so that adding or removing a
// server moves only about 1/N of the keys. With plain "hash(key) mod N",
// changing N reshuffles almost everything (a cache cluster would lose nearly
// all hits at once). Instead, servers and keys are hashed onto the same ring
// of 2^32 positions; a key belongs to the first server clockwise from it.
// Removing a server hands only its arcs to the next servers; adding one takes
// arcs only from its neighbours. With one point per server the arcs are very
// uneven, so each server gets V "virtual nodes" (points at hash(name#i)),
// which evens out the load and spreads a leaving server's keys over many
// others. Lookup is a binary search over the sorted points: O(log(N*V)).
// The hash must be stable across processes (here FNV-1a plus a mix), never
// Object.hashCode of something identity-based.
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

public class ConsistentHashRing {
    private final int virtualNodes;
    private final List<String> servers = new ArrayList<>();
    private long[] points = new long[0];   // sorted ring positions
    private String[] owners = new String[0]; // owners[i] owns points[i]

    ConsistentHashRing(int virtualNodes) {
        this.virtualNodes = virtualNodes;
    }

    // Unsigned 32-bit position on the ring, stored in a long
    static long hash(String s) {
        int h = 0x811c9dc5;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= b & 0xff;
            h *= 0x01000193;
        }
        h ^= h >>> 16; // murmur3 fmix32: FNV alone clusters on similar strings
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return Integer.toUnsignedLong(h);
    }

    void addServer(String server) {
        servers.add(server);
        rebuild();
    }

    void removeServer(String server) {
        servers.remove(server);
        rebuild();
    }

    private void rebuild() {
        int total = servers.size() * virtualNodes;
        long[][] entries = new long[total][2]; // {position, server index}
        int i = 0;
        for (int s = 0; s < servers.size(); s++) {
            for (int v = 0; v < virtualNodes; v++) {
                entries[i][0] = hash(servers.get(s) + "#" + v);
                entries[i][1] = s;
                i++;
            }
        }
        Arrays.sort(entries, (a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0])
                : servers.get((int) a[1]).compareTo(servers.get((int) b[1])));
        points = new long[total];
        owners = new String[total];
        for (int j = 0; j < total; j++) {
            points[j] = entries[j][0];
            owners[j] = servers.get((int) entries[j][1]);
        }
    }

    String serverFor(String key) {
        long h = hash(key);
        // first point >= h (lower bound); past the end wraps to point 0
        int lo = 0, hi = points.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (points[mid] < h) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return owners[lo == points.length ? 0 : lo];
    }

    // Reference lookup with TreeMap.ceilingEntry, for the self-check
    String serverForTreeMap(String key) {
        TreeMap<Long, String> ring = new TreeMap<>();
        for (int j = points.length - 1; j >= 0; j--) {
            ring.put(points[j], owners[j]); // first owner wins on (unlikely) collisions
        }
        var e = ring.ceilingEntry(hash(key));
        return (e != null ? e : ring.firstEntry()).getValue();
    }

    static String modN(String key, List<String> servers) {
        return servers.get((int) (hash(key) % servers.size()));
    }

    static String[] assign(ConsistentHashRing ring, List<String> keys) {
        return keys.stream().map(ring::serverFor).toArray(String[]::new);
    }

    static int moved(String[] before, String[] after) {
        int c = 0;
        for (int i = 0; i < before.length; i++) {
            c += before[i].equals(after[i]) ? 0 : 1;
        }
        return c;
    }

    static String load(String[] owners, List<String> servers) {
        TreeMap<String, Integer> counts = new TreeMap<>();
        servers.forEach(s -> counts.put(s, 0));
        for (String o : owners) {
            counts.merge(o, 1, Integer::sum);
        }
        return counts.toString();
    }

    public static void main(String[] args) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) {
            keys.add("session:" + i);
        }
        List<String> four = List.of("cache-a", "cache-b", "cache-c", "cache-d");
        List<String> five = List.of("cache-a", "cache-b", "cache-c", "cache-d", "cache-e");

        // mod-N: adding a fifth server
        int modMoved = 0;
        for (String k : keys) {
            modMoved += modN(k, four).equals(modN(k, five)) ? 0 : 1;
        }
        System.out.printf(Locale.ROOT, "mod-N, 4 -> 5 servers: %.1f%% of keys moved%n", 100.0 * modMoved / keys.size());

        for (int v : new int[] {1, 100}) {
            ConsistentHashRing ring = new ConsistentHashRing(v);
            four.forEach(ring::addServer);
            String[] before = assign(ring, keys);
            System.out.println("ring, " + v + " virtual node(s) per server");
            System.out.println("  load with 4: " + load(before, four));

            ring.addServer("cache-e");
            String[] added = assign(ring, keys);
            int m = moved(before, added);
            boolean onlyToNew = true;
            for (int i = 0; i < before.length; i++) {
                onlyToNew &= before[i].equals(added[i]) || added[i].equals("cache-e");
            }
            System.out.printf(Locale.ROOT, "  add cache-e: %.1f%% moved, all of them to cache-e: %b%n",
                    100.0 * m / keys.size(), onlyToNew);

            ring.removeServer("cache-b");
            String[] removed = assign(ring, keys);
            boolean onlyFromB = true;
            for (int i = 0; i < added.length; i++) {
                onlyFromB &= added[i].equals(removed[i]) || added[i].equals("cache-b");
            }
            System.out.printf(Locale.ROOT, "  remove cache-b: %.1f%% moved, all of them from cache-b: %b%n",
                    100.0 * moved(added, removed) / keys.size(), onlyFromB);
            System.out.println("  load after: " + load(removed, List.of("cache-a", "cache-c", "cache-d", "cache-e")));

            boolean same = true;
            for (int i = 0; i < 2_000; i++) {
                same &= ring.serverFor(keys.get(i)).equals(ring.serverForTreeMap(keys.get(i)));
            }
            System.out.println("  binary search matches TreeMap.ceilingEntry: " + same);
        }
    }
}

/* Output:
mod-N, 4 -> 5 servers: 79.9% of keys moved
ring, 1 virtual node(s) per server
  load with 4: {cache-a=22785, cache-b=14416, cache-c=20365, cache-d=42434}
  add cache-e: 1.9% moved, all of them to cache-e: true
  remove cache-b: 12.6% moved, all of them from cache-b: true
  load after: {cache-a=22785, cache-c=32916, cache-d=42434, cache-e=1865}
  binary search matches TreeMap.ceilingEntry: true
ring, 100 virtual node(s) per server
  load with 4: {cache-a=28220, cache-b=23862, cache-c=23959, cache-d=23959}
  add cache-e: 19.0% moved, all of them to cache-e: true
  remove cache-b: 19.9% moved, all of them from cache-b: true
  load after: {cache-a=29418, cache-c=25824, cache-d=21626, cache-e=23132}
  binary search matches TreeMap.ceilingEntry: true
*/
