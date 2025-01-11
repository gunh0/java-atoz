// VNA02-J. Ensure that compound operations on shared variables are atomic
// https://wiki.sei.cmu.edu/confluence/display/java/VNA02-J.+Ensure+that+compound+operations+on+shared+variables+are+atomic
//
// count++, balance -= x and flag = !flag look like one step but are three:
// read the field, compute, write it back. When two threads interleave
// between the read and the write, one update overwrites the other (a "lost
// update"). volatile does not help: it makes each read and write visible,
// not the read-modify-write sequence atomic. For security this matters for
// counters that enforce limits: failed-login counters, rate limits, quotas
// and single-use tokens. Use an atomic class (AtomicInteger.incrementAndGet,
// LongAdder) or guard the whole sequence with one lock.
// Real races depend on timing; this demo makes the bad interleaving certain
// by splitting the increment into its read and write steps with a latch in
// between, so both threads read the old value before either writes.
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class VNA02_CompoundOperations {
    // Noncompliant: volatile, but "failures++" is still read, add, write
    static class LoginGuardNoncompliant {
        private volatile int failures;

        // The hook runs between the read and the write, where a real thread
        // could be preempted at any time
        void recordFailure(Runnable betweenReadAndWrite) {
            int read = failures;          // 1. read
            betweenReadAndWrite.run();
            failures = read + 1;          // 2. add and 3. write
        }

        int failures() {
            return failures;
        }
    }

    // Compliant: one atomic read-modify-write instruction
    static class LoginGuardAtomic {
        private final AtomicInteger failures = new AtomicInteger();

        int recordFailure() {
            return failures.incrementAndGet();
        }

        int failures() {
            return failures.get();
        }
    }

    // Compliant: the whole check-and-update sequence under one lock
    static class LoginGuardLocked {
        private final Object lock = new Object();
        private int failures;
        private boolean locked;

        void recordFailure() {
            synchronized (lock) {
                failures++;
                if (failures >= 2) {
                    locked = true;
                }
            }
        }

        boolean isLocked() {
            synchronized (lock) {
                return locked;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // Two failed logins arrive at the same time
        LoginGuardNoncompliant bad = new LoginGuardNoncompliant();
        CountDownLatch bothRead = new CountDownLatch(2);
        Runnable waitForOtherReader = () -> {
            bothRead.countDown();
            try {
                bothRead.await(); // do not write until the other thread has read too
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        runTwice(() -> bad.recordFailure(waitForOtherReader));
        System.out.println("noncompliant: 2 failures recorded as " + bad.failures());

        LoginGuardAtomic atomic = new LoginGuardAtomic();
        runTwice(atomic::recordFailure);
        System.out.println("atomic:       2 failures recorded as " + atomic.failures());

        LoginGuardLocked locked = new LoginGuardLocked();
        runTwice(locked::recordFailure);
        System.out.println("locked:       account locked after 2 failures: " + locked.isLocked());

        // At scale: 8 threads x 10_000 increments, no tricks needed
        AtomicInteger counter = new AtomicInteger();
        Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 10_000; j++) {
                    counter.incrementAndGet();
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
        System.out.println("atomic counter after 80000 increments: " + counter.get());
    }

    static void runTwice(Runnable task) throws InterruptedException {
        Thread a = new Thread(task);
        Thread b = new Thread(task);
        a.start();
        b.start();
        a.join();
        b.join();
    }
}

/* Output:
noncompliant: 2 failures recorded as 1
atomic:       2 failures recorded as 2
locked:       account locked after 2 failures: true
atomic counter after 80000 increments: 80000
*/
