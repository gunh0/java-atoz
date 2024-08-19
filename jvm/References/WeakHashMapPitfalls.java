// WeakHashMap: entries that vanish with their keys, and two ways to leak anyway
//
// WeakHashMap holds its KEYS through weak references: once nothing else
// references a key, the GC clears it and the map drops the entry on its next
// operation (it drains an internal ReferenceQueue). That makes it a fit for
// attaching data to objects you do not own ("metadata per Class, per
// Thread, per session") without keeping them alive. Two classic mistakes
// defeat it: (1) the VALUE refers back to its key, so the map itself keeps
// the key strongly reachable and the entry never goes away; (2) the key is
// something that is always reachable anyway, such as a string literal
// (interned, referenced from the class) or a boxed small Integer (cached).
// Keys are compared with equals(), not identity; use a weak identity map
// for objects with value-based equals. GC is triggered in a bounded loop.
import java.util.Map;
import java.util.WeakHashMap;

public class WeakHashMapPitfalls {
    static final class Session {
        final String id;
        Session(String id) { this.id = id; }
    }

    record Metadata(String note) { }
    record LeakyMetadata(Session owner, String note) { } // points back to its key

    public static void main(String[] args) throws Exception {
        // 1. Works as intended: the entry goes away with its key
        Map<Session, Metadata> good = new WeakHashMap<>();
        Session s1 = new Session("s1");
        good.put(s1, new Metadata("logged in"));
        System.out.println("good map size while session is in use: " + good.size());
        s1 = null;
        System.out.println("good map empty after the session is dropped: " + gcUntil(good::isEmpty));

        // 2. Value references the key: never collected
        Map<Session, LeakyMetadata> leaky = new WeakHashMap<>();
        Session s2 = new Session("s2");
        leaky.put(s2, new LeakyMetadata(s2, "logged in"));
        s2 = null;
        System.out.println("leaky map empty after the session is dropped: " + gcUntil(leaky::isEmpty) + " (size " + leaky.size() + ")");

        // 3. Keys that are always reachable: string literals, cached Integers
        Map<Object, String> pinned = new WeakHashMap<>();
        pinned.put("config", "literal key");
        pinned.put(Integer.valueOf(42), "cached Integer key");
        pinned.put(new String("temp"), "fresh String key");
        System.out.println("pinned map after GC: only the fresh key went away: "
                + gcUntil(() -> pinned.size() == 2) + " (left: " + pinned.values().stream().sorted().toList() + ")");
    }

    interface Condition { boolean holds(); }

    static boolean gcUntil(Condition c) throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            if (c.holds()) return true;
            System.gc();
            Thread.sleep(20);
        }
        return c.holds();
    }
}

/* Output:
good map size while session is in use: 1
good map empty after the session is dropped: true
leaky map empty after the session is dropped: false (size 1)
pinned map after GC: only the fresh key went away: true (left: [cached Integer key, literal key])
*/
