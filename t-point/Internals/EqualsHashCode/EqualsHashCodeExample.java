// How HashMap uses hashCode() and equals():
//  1. hash = key.hashCode() ^ (key.hashCode() >>> 16)  (spreads the high bits)
//  2. bucket = hash & (table.length - 1)              (table length is a power of 2)
//  3. inside the bucket, entries are compared with == / equals()
// So hashCode() picks the bucket and equals() picks the entry. Consequences:
//  - equals() without a matching hashCode(): equal keys land in different
//    buckets and get() misses
//  - a constant hashCode() is legal but puts everything in one bucket
//    (Java 8+ turns a crowded bucket into a tree, which only helps for
//    Comparable keys)
//  - mutating a field used by hashCode() after insertion "loses" the entry
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class EqualsHashCodeExample {
    // equals() overridden, hashCode() forgotten
    static final class BrokenKey {
        final String host;
        final int port;

        BrokenKey(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof BrokenKey k && k.port == port && k.host.equals(host);
        }
    }

    // Both overridden consistently, but mutable
    static final class MutableKey {
        String host;
        int port;

        MutableKey(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof MutableKey k && k.port == port && k.host.equals(host);
        }

        @Override
        public int hashCode() { return Objects.hash(host, port); }
    }

    // The fix: immutable, and a record generates equals/hashCode for us
    record Endpoint(String host, int port) {}

    static int bucketOf(Object key, int tableLength) {
        int h = key.hashCode();
        return (h ^ (h >>> 16)) & (tableLength - 1);
    }

    public static void main(String[] args) {
        Map<BrokenKey, String> broken = new HashMap<>();
        broken.put(new BrokenKey("db.internal", 5432), "postgres");
        BrokenKey lookup = new BrokenKey("db.internal", 5432);
        System.out.println("BrokenKey: equals=" + lookup.equals(broken.keySet().iterator().next())
                + ", get=" + broken.get(lookup));

        Map<Endpoint, String> fixed = new HashMap<>();
        fixed.put(new Endpoint("db.internal", 5432), "postgres");
        System.out.println("Endpoint record: get=" + fixed.get(new Endpoint("db.internal", 5432)));

        // Where keys go in a 16-bucket table (String.hashCode is specified, so this is stable)
        for (String k : new String[] {"Aa", "BB", "admin", "root"}) {
            System.out.printf("  %-6s hashCode=%-9d bucket=%d%n", k, k.hashCode(), bucketOf(k, 16));
        }
        System.out.println("  \"Aa\" and \"BB\" collide: same bucket, told apart by equals()");

        Map<String, Integer> collisions = new HashMap<>();
        collisions.put("Aa", 1);
        collisions.put("BB", 2);
        System.out.println("  get(Aa)=" + collisions.get("Aa") + " get(BB)=" + collisions.get("BB"));

        // Mutating a key after insertion
        Set<MutableKey> seen = new HashSet<>();
        MutableKey key = new MutableKey("cache.internal", 6379);
        seen.add(key);
        key.port = 6380;
        System.out.println("after mutating the key: contains(same object)=" + seen.contains(key)
                + ", size=" + seen.size());
        boolean found = false;
        for (MutableKey k : seen) found |= k == key;
        System.out.println("but iteration still finds it: " + found + "; remove() works: " + seen.remove(key));
    }
}

/* Output:
BrokenKey: equals=true, get=null
Endpoint record: get=postgres
  Aa     hashCode=2112      bucket=0
  BB     hashCode=2112      bucket=0
  admin  hashCode=92668751  bucket=9
  root   hashCode=3506402   bucket=7
  "Aa" and "BB" collide: same bucket, told apart by equals()
  get(Aa)=1 get(BB)=2
after mutating the key: contains(same object)=false, size=1
but iteration still finds it: true; remove() works: false
*/
