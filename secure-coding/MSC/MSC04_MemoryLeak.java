// MSC04-J. Do not leak memory
// https://wiki.sei.cmu.edu/confluence/display/java/MSC04-J.+Do+not+leak+memory
//
// The garbage collector frees only objects that are unreachable. A Java
// "memory leak" is a reference that is kept although the program will
// never use it again: an array slot past the logical end of a hand-written
// stack, a cache or listener list that only grows, a map keyed by objects
// whose hashCode() changes. Each leaked entry also keeps everything it
// refers to alive, often including sensitive data, and an attacker who can
// drive the growth (one cache entry per request) can exhaust the heap.
// Clear references you no longer need (null out slots, remove entries,
// bound caches), and use keys with stable equals()/hashCode().
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MSC04_MemoryLeak {
    public static void main(String[] args) {
        // 1. A hand-written stack that forgets to clear popped slots
        StackNoncompliant bad = new StackNoncompliant();
        StackCompliant good = new StackCompliant();
        for (int i = 0; i < 1_000; i++) {
            bad.push(new byte[1024]);
            good.push(new byte[1024]);
        }
        while (!bad.isEmpty()) bad.pop();
        while (!good.isEmpty()) good.pop();
        System.out.println("noncompliant stack: empty, but still references " + bad.retained() + " buffers");
        System.out.println("compliant stack:    empty, references " + good.retained() + " buffers");

        // 2. A map keyed by an object whose hash changes after insertion
        Map<MutableKey, String> sessions = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            MutableKey key = new MutableKey("user" + i);
            sessions.put(key, "session data " + i);
            key.name = key.name + "-renamed"; // mutated while in the map
            sessions.remove(key);             // looks in the wrong bucket, removes nothing
        }
        System.out.println("noncompliant map: entries left after removing all 100: " + sessions.size());

        Map<String, String> fixed = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            String key = "user" + i; // immutable key
            fixed.put(key, "session data " + i);
            fixed.remove(key);
        }
        System.out.println("compliant map:    entries left after removing all 100: " + fixed.size());
    }
}

// Noncompliant: pop() leaves the reference in the array
class StackNoncompliant {
    private Object[] elements = new Object[16];
    private int size;

    void push(Object o) {
        if (size == elements.length) {
            elements = Arrays.copyOf(elements, size * 2);
        }
        elements[size++] = o;
    }

    Object pop() {
        return elements[--size];
    }

    boolean isEmpty() {
        return size == 0;
    }

    long retained() {
        return Arrays.stream(elements).filter(Objects::nonNull).count();
    }
}

// Compliant: pop() clears the slot
class StackCompliant {
    private Object[] elements = new Object[16];
    private int size;

    void push(Object o) {
        if (size == elements.length) {
            elements = Arrays.copyOf(elements, size * 2);
        }
        elements[size++] = o;
    }

    Object pop() {
        Object o = elements[--size];
        elements[size] = null; // let the GC reclaim it
        return o;
    }

    boolean isEmpty() {
        return size == 0;
    }

    long retained() {
        return Arrays.stream(elements).filter(Objects::nonNull).count();
    }
}

class MutableKey {
    String name;

    MutableKey(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MutableKey k && name.equals(k.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}

/* Output:
noncompliant stack: empty, but still references 1000 buffers
compliant stack:    empty, references 0 buffers
noncompliant map: entries left after removing all 100: 100
compliant map:    entries left after removing all 100: 0
*/
