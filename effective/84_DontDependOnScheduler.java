// Effective Java, Item 84: Don't depend on the thread scheduler.
//
// When many threads are runnable, the scheduler decides who runs and for how
// long, and the policy differs between operating systems and JVMs. A program
// whose correctness or performance depends on that policy is not portable.
// Keep the number of runnable threads close to the number of processors:
// threads should not busy-wait for shared state to change, and should not be
// started for tiny tasks. Thread.yield and thread priorities are not fixes -
// they have no testable semantics. Instead, block on a proper synchronizer
// (CountDownLatch, BlockingQueue, ...) so waiting threads use no CPU at all.
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DontDependOnScheduler {
    // Anti-pattern: a latch whose waiters spin, burning a CPU each
    static final class BusyWaitLatch {
        private int count;

        BusyWaitLatch(int count) {
            this.count = count;
        }

        void await() {
            while (true) {
                synchronized (this) {
                    if (count == 0) {
                        return;
                    }
                }
                // spinning here; Thread.yield() would not make it correct or portable
            }
        }

        synchronized void countDown() {
            if (count != 0) {
                count--;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        int waiters = 2, parties = 3;

        // Both versions release all waiters, but only one does it efficiently
        BusyWaitLatch busy = new BusyWaitLatch(parties);
        AtomicInteger busyReleased = new AtomicInteger();
        try (ExecutorService exec = Executors.newFixedThreadPool(waiters)) {
            for (int i = 0; i < waiters; i++) {
                exec.execute(() -> {
                    busy.await(); // stays RUNNABLE, competing with real work
                    busyReleased.incrementAndGet();
                });
            }
            for (int i = 0; i < parties; i++) {
                busy.countDown();
            }
        }
        System.out.println("busy-wait latch:  released " + busyReleased.get() + " of " + waiters);

        CountDownLatch latch = new CountDownLatch(parties);
        AtomicInteger released = new AtomicInteger();
        Thread[] threads = new Thread[waiters];
        for (int i = 0; i < waiters; i++) {
            threads[i] = new Thread(() -> {
                try {
                    latch.await(); // parks: WAITING, uses no CPU
                    released.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads[i].start();
        }
        // While the latch is closed, a blocked waiter is not runnable at all
        while (threads[0].getState() != Thread.State.WAITING) {
            Thread.onSpinWait();
        }
        System.out.println("CountDownLatch:   waiter state while closed = " + threads[0].getState());
        for (int i = 0; i < parties; i++) {
            latch.countDown();
        }
        for (Thread t : threads) {
            t.join(TimeUnit.SECONDS.toMillis(5));
        }
        System.out.println("CountDownLatch:   released " + released.get() + " of " + waiters);
    }
}

/* Output:
busy-wait latch:  released 2 of 2
CountDownLatch:   waiter state while closed = WAITING
CountDownLatch:   released 2 of 2
*/
