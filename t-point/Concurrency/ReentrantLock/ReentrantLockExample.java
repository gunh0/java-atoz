// ReentrantLock does what synchronized does (mutual exclusion, visibility,
// reentrancy) and adds what synchronized cannot:
//  - tryLock() / tryLock(timeout): give up instead of blocking forever, a
//    simple way out of deadlock-prone code
//  - lockInterruptibly(): waiting for the lock can be interrupted
//  - several Condition objects per lock (await/signal), where an intrinsic
//    lock has a single wait set (wait/notify)
//  - optional fairness: new ReentrantLock(true) hands the lock out in FIFO order
// The price is discipline: always unlock() in a finally block.
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ReentrantLockExample {
    // A key store whose rotation must not overlap with signing
    static final class KeyStore {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition rotated = lock.newCondition();
        private int keyVersion = 1;

        void rotate() {
            lock.lock();
            try {
                keyVersion++;
                audit("rotated to v" + keyVersion); // re-enters the same lock
                rotated.signalAll(); // wake every thread waiting for the new key
            } finally {
                lock.unlock();
            }
        }

        private void audit(String event) {
            lock.lock(); // reentrant: the owner may lock again
            try {
                System.out.println("audit: " + event + " (hold count " + lock.getHoldCount() + ")");
            } finally {
                lock.unlock();
            }
        }

        // Wait (releasing the lock) until the key is at least the given version
        int awaitVersion(int version) throws InterruptedException {
            lock.lock();
            try {
                while (keyVersion < version) { // always re-check in a loop
                    if (!rotated.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
                }
                return keyVersion;
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        KeyStore store = new KeyStore();
        ExecutorService pool = Executors.newFixedThreadPool(3);

        // Condition: three signers wait for key v2 before signing
        List<java.util.concurrent.Future<Integer>> signers = new ArrayList<>();
        for (int i = 0; i < 3; i++) signers.add(pool.submit(() -> store.awaitVersion(2)));
        Thread.sleep(50); // let them start waiting (correct even if they have not)
        store.rotate();
        List<Integer> versions = new ArrayList<>();
        for (var f : signers) versions.add(f.get());
        System.out.println("signers used key versions " + versions);

        // tryLock: another thread holds the lock, so we do not block
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        pool.execute(() -> {
            store.lock.lock();
            try {
                held.countDown();
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                store.lock.unlock();
            }
        });
        held.await();
        System.out.println("isLocked: " + store.lock.isLocked() + ", by me: " + store.lock.isHeldByCurrentThread());
        System.out.println("tryLock(): " + store.lock.tryLock());
        System.out.println("tryLock(50 ms): " + store.lock.tryLock(50, TimeUnit.MILLISECONDS));
        release.countDown();
        boolean got = store.lock.tryLock(2, TimeUnit.SECONDS);
        System.out.println("tryLock after release: " + got);
        if (got) store.lock.unlock();

        // unlock() by a thread that does not own the lock is an error
        try {
            store.lock.unlock();
        } catch (IllegalMonitorStateException e) {
            System.out.println("unlock without owning it: IllegalMonitorStateException");
        }

        pool.shutdown();
        System.out.println("terminated: " + pool.awaitTermination(2, TimeUnit.SECONDS));
    }
}

/* Output:
audit: rotated to v2 (hold count 2)
signers used key versions [2, 2, 2]
isLocked: true, by me: false
tryLock(): false
tryLock(50 ms): false
tryLock after release: true
unlock without owning it: IllegalMonitorStateException
terminated: true
*/
