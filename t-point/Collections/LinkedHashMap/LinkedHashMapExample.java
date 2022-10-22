// LinkedHashMap is a HashMap with a doubly linked list through its entries,
// so iteration has a predictable order:
//  - insertion order (default): re-putting an existing key does NOT move it
//  - access order (constructor flag true): get/put move the entry to the end,
//    so the first entry is always the least recently used one
// Overriding removeEldestEntry() turns an access-ordered LinkedHashMap into a
// complete LRU cache in a few lines.
import java.util.LinkedHashMap;
import java.util.Map;

public class LinkedHashMapExample {
    public static void main(String[] args) {
        // Insertion order: handy for keeping HTTP headers in the order received
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Host", "example.org");
        headers.put("Accept", "text/html");
        headers.put("User-Agent", "demo/1.0");
        headers.put("Host", "example.com"); // value replaced, position kept
        System.out.println("insertion order: " + headers);

        // Access order: every get() moves the key to the end
        Map<String, Integer> accessOrdered = new LinkedHashMap<>(16, 0.75f, true);
        accessOrdered.put("a", 1);
        accessOrdered.put("b", 2);
        accessOrdered.put("c", 3);
        accessOrdered.get("a");
        System.out.println("access order after get(a): " + accessOrdered);

        // LRU cache with a fixed capacity
        LruCache<String, String> cache = new LruCache<>(3);
        cache.put("/home", "<home>");
        cache.put("/about", "<about>");
        cache.put("/blog", "<blog>");
        System.out.println("cache: " + cache.keySet());
        cache.get("/home");                   // /home becomes most recently used
        System.out.println("after get(/home): " + cache.keySet());
        cache.put("/contact", "<contact>");   // evicts /about, the least recently used
        System.out.println("after put(/contact): " + cache.keySet());
        System.out.println("evicted: " + cache.evicted);
    }
}

class LruCache<K, V> extends LinkedHashMap<K, V> {
    private final int capacity;
    final java.util.List<K> evicted = new java.util.ArrayList<>();

    LruCache(int capacity) {
        super(16, 0.75f, true); // true = access order
        this.capacity = capacity;
    }

    // Called by put() after inserting; returning true removes the eldest entry
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        boolean full = size() > capacity;
        if (full) {
            evicted.add(eldest.getKey());
        }
        return full;
    }
}

/* Output:
insertion order: {Host=example.com, Accept=text/html, User-Agent=demo/1.0}
access order after get(a): {b=2, c=3, a=1}
cache: [/home, /about, /blog]
after get(/home): [/about, /blog, /home]
after put(/contact): [/blog, /home, /contact]
evicted: [/about]
*/
