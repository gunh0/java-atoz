// Effective Java, Item 78: Synchronize access to shared mutable data.
//
// Synchronization does two things: mutual exclusion (no thread sees an
// object mid-update) and visibility (a thread sees the effects of writes
// made by others under the same lock). Without it, one thread may *never*
// see another's write - a loop polling a plain boolean "stop" flag can spin
// forever, because the JIT may hoist the read out of the loop - and
// read-modify-write operations like count++ lose updates. volatile fixes
// visibility only; for compound actions use synchronized or the atomic
// classes. Best of all, don't share mutable data: confine it to one thread
// or share only immutable data. Note: both reads and writes need the lock.
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class SynchronizeSharedMutableData {
    // Broken (not executed here, it may never terminate):
    //   static boolean stopRequested;
    //   new Thread(() -> { while (!stopRequested) i++; }).start();
    //   stopRequested = true;   // the thread may never observe this
    private static volatile boolean stopRequested; // volatile: writes are visible

    private static volatile long volatileCounter; // visible, but ++ is not atomic
    private static long syncCounter;
    private static final AtomicLong atomicCounter = new AtomicLong();

    private static synchronized void incrementSync() {
        syncCounter++;
    }

    public static void main(String[] args) throws InterruptedException {
        Thread background = new Thread(() -> {
            long i = 0;
            while (!stopRequested) {
                i++;
            }
        });
        background.start();
        Thread.sleep(100);
        stopRequested = true;
        background.join(5_000);
        System.out.println("volatile stop flag: background thread stopped = " + !background.isAlive());

        int threads = 4, perThread = 200_000;
        long expected = (long) threads * perThread;
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int t = 0; t < threads; t++) {
                pool.execute(() -> {
                    for (int i = 0; i < perThread; i++) {
                        volatileCounter++;              // read, add, write: not atomic
                        incrementSync();                // mutual exclusion
                        atomicCounter.incrementAndGet(); // lock-free atomic update
                    }
                });
            }
        }
        // The volatile counter usually loses updates; it can never gain any
        System.out.println("expected count:           " + expected);
        System.out.println("volatile ++ <= expected:  " + (volatileCounter <= expected));
        System.out.println("synchronized count:       " + syncCounter);
        System.out.println("AtomicLong count:         " + atomicCounter.get());
    }
}

/* Output:
volatile stop flag: background thread stopped = true
expected count:           800000
volatile ++ <= expected:  true
synchronized count:       800000
AtomicLong count:         800000
*/
