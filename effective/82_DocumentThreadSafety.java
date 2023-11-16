// Effective Java, Item 82: Document thread safety.
//
// The presence of `synchronized` in a method declaration is an
// implementation detail, not part of the API; clients need a documented
// level of thread safety: immutable, unconditionally thread-safe,
// conditionally thread-safe (some sequences need external locking - say
// which lock), not thread-safe, or thread-hostile. Example of the
// conditional case: iterating a Collections.synchronizedList requires
// holding the list's lock. An unconditionally thread-safe class should
// prefer a private final lock object over `synchronized` methods: a public
// lock (the instance itself) lets any client hold it and stall the class,
// accidentally or as a denial-of-service.
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DocumentThreadSafety {
    /** Thread-safe, but locks on {@code this}: anyone can hold that lock. */
    static final class PublicLockCounter {
        private int count;

        synchronized void increment() {
            count++;
        }
    }

    /** Unconditionally thread-safe; uses a private lock clients cannot reach. */
    static final class PrivateLockCounter {
        private final Object lock = new Object();
        private int count;

        void increment() {
            synchronized (lock) {
                count++;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        PublicLockCounter exposed = new PublicLockCounter();
        PrivateLockCounter hidden = new PrivateLockCounter();

        // A misbehaving client grabs the instance locks and holds them
        synchronized (exposed) {
            synchronized (hidden) {
                Thread t1 = new Thread(exposed::increment);
                Thread t2 = new Thread(hidden::increment);
                t1.start();
                t2.start();
                t1.join(300);
                t2.join(300);
                System.out.println("public lock:  increment finished while client holds lock = " + !t1.isAlive());
                System.out.println("private lock: increment finished while client holds lock = " + !t2.isAlive());
            }
        }

        // Conditionally thread-safe: iteration needs the documented lock
        List<Integer> list = Collections.synchronizedList(new ArrayList<>(List.of(1, 2, 3)));
        int sum = 0;
        synchronized (list) { // required by the Collections.synchronizedList contract
            for (int x : list) {
                sum += x;
            }
        }
        System.out.println("synchronizedList iterated under its own lock: sum = " + sum);
    }
}

/* Output:
public lock:  increment finished while client holds lock = false
private lock: increment finished while client holds lock = true
synchronizedList iterated under its own lock: sum = 6
*/
