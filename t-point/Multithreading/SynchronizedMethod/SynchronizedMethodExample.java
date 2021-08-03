// A synchronized method acquires the monitor (lock) of `this` before it runs
// (or of the Class object for a static synchronized method) and releases it
// when it returns or throws. Only one thread at a time can be inside any
// synchronized method of the same object, and the lock also guarantees that
// changes made inside are visible to the next thread that takes it.
// count++ is NOT atomic: it is read, add, write. Without synchronization two
// threads can read the same value and one increment is lost.
import java.util.ArrayList;
import java.util.List;

public class SynchronizedMethodExample {
    private static final int THREADS = 4;
    private static final int INCREMENTS = 100_000;

    public static void main(String[] args) throws InterruptedException {
        UnsafeCounter unsafe = new UnsafeCounter();
        SafeCounter safe = new SafeCounter();

        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            threads.add(new Thread(() -> {
                for (int i = 0; i < INCREMENTS; i++) {
                    unsafe.increment();
                    safe.increment();
                }
            }));
        }
        threads.forEach(Thread::start);
        for (Thread t : threads) {
            t.join();
        }

        int expected = THREADS * INCREMENTS;
        System.out.println("expected:            " + expected);
        System.out.println("synchronized count:  " + safe.get());
        // The unsynchronized value varies from run to run, so only its bound is
        // printed: lost updates can make it smaller, never larger.
        System.out.println("unsynchronized <= expected: " + (unsafe.get() <= expected));

        // static synchronized locks the Class object, shared by all instances
        Thread a = new Thread(() -> IdGenerator.next());
        Thread b = new Thread(() -> IdGenerator.next());
        a.start();
        b.start();
        a.join();
        b.join();
        System.out.println("ids handed out: " + IdGenerator.next());
    }
}

class UnsafeCounter {
    private int count;

    void increment() {
        count++; // race condition: read-modify-write without a lock
    }

    int get() {
        return count;
    }
}

class SafeCounter {
    private int count;

    synchronized void increment() {
        count++;
    }

    // Reads are synchronized too, so the caller sees the latest value
    synchronized int get() {
        return count;
    }
}

class IdGenerator {
    private static int next = 1;

    static synchronized int next() {
        return next++;
    }
}

/* Output:
expected:            400000
synchronized count:  400000
unsynchronized <= expected: true
ids handed out: 3
*/
