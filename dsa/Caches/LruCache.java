// LRU (least recently used) cache with O(1) get and put: a hash map finds the
// node for a key, and a doubly linked list keeps nodes in recency order (most
// recent right after the head sentinel, least recent right before the tail).
// get() moves the node to the front; put() inserts at the front and, when over
// capacity, evicts the node before the tail. The JDK can do the same with
// LinkedHashMap(capacity, loadFactor, accessOrder = true) and removeEldestEntry,
// which the self-check uses as the reference.
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class LruCache<K, V> {
    private static final class Node<K, V> {
        K key;
        V value;
        Node<K, V> prev, next;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private final int capacity;
    private final Map<K, Node<K, V>> index = new HashMap<>();
    private final Node<K, V> head = new Node<>(null, null); // sentinels
    private final Node<K, V> tail = new Node<>(null, null);
    private final List<K> evicted = new ArrayList<>();

    public LruCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        head.next = tail;
        tail.prev = head;
    }

    public V get(K key) {
        Node<K, V> node = index.get(key);
        if (node == null) {
            return null;
        }
        unlink(node);
        linkFirst(node);
        return node.value;
    }

    public void put(K key, V value) {
        Node<K, V> node = index.get(key);
        if (node != null) {
            node.value = value;
            unlink(node);
            linkFirst(node);
            return;
        }
        node = new Node<>(key, value);
        index.put(key, node);
        linkFirst(node);
        if (index.size() > capacity) {
            Node<K, V> lru = tail.prev;
            unlink(lru);
            index.remove(lru.key);
            evicted.add(lru.key);
        }
    }

    public int size() {
        return index.size();
    }

    List<K> evicted() {
        return evicted;
    }

    // Keys from most to least recently used
    List<K> keys() {
        List<K> keys = new ArrayList<>();
        for (Node<K, V> n = head.next; n != tail; n = n.next) {
            keys.add(n.key);
        }
        return keys;
    }

    private void unlink(Node<K, V> n) {
        n.prev.next = n.next;
        n.next.prev = n.prev;
    }

    private void linkFirst(Node<K, V> n) {
        n.next = head.next;
        n.prev = head;
        head.next.prev = n;
        head.next = n;
    }

    // Reference implementation on top of LinkedHashMap
    static <K, V> Map<K, V> jdkLru(int capacity) {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > capacity;
            }
        };
    }

    public static void main(String[] args) {
        LruCache<String, String> sessions = new LruCache<>(3);
        sessions.put("alice", "s-1");
        sessions.put("bob", "s-2");
        sessions.put("carol", "s-3");
        System.out.println("recency:       " + sessions.keys());
        System.out.println("get(alice):    " + sessions.get("alice") + " -> " + sessions.keys());
        sessions.put("dave", "s-4"); // evicts bob, the least recently used
        System.out.println("put(dave):     " + sessions.keys() + " evicted " + sessions.evicted());
        System.out.println("get(bob):      " + sessions.get("bob"));
        sessions.put("carol", "s-3b"); // update counts as a use
        sessions.put("erin", "s-5");
        System.out.println("put(carol), put(erin): " + sessions.keys() + " evicted " + sessions.evicted());

        // Self-check: same hits, misses and contents as a LinkedHashMap in access order
        Random rnd = new Random(23);
        LruCache<Integer, Integer> mine = new LruCache<>(50);
        Map<Integer, Integer> ref = jdkLru(50);
        boolean ok = true;
        int hits = 0;
        int ops = 100_000;
        for (int i = 0; i < ops; i++) {
            int key = (int) Math.abs(rnd.nextGaussian() * 40); // skewed key popularity
            if (rnd.nextInt(3) == 0) {
                mine.put(key, i);
                ref.put(key, i);
            } else {
                Integer a = mine.get(key), b = ref.get(key);
                ok &= java.util.Objects.equals(a, b);
                if (a != null) {
                    hits++;
                }
            }
        }
        List<Integer> refOrder = new ArrayList<>(ref.keySet()); // eldest first
        java.util.Collections.reverse(refOrder);
        ok &= refOrder.equals(mine.keys()) && mine.size() == ref.size();
        System.out.println(ops + " random ops match LinkedHashMap(accessOrder=true): " + ok
                + ", hit rate " + (hits * 100 / (ops - ops / 3)) + "%");
    }
}

/* Output:
recency:       [carol, bob, alice]
get(alice):    s-1 -> [alice, carol, bob]
put(dave):     [dave, alice, carol] evicted [bob]
get(bob):      null
put(carol), put(erin): [erin, carol, dave] evicted [bob, alice]
100000 random ops match LinkedHashMap(accessOrder=true): true, hit rate 68%
*/
