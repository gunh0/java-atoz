// LCK03-J. Do not synchronize on the intrinsic locks of high-level concurrency objects
// https://wiki.sei.cmu.edu/confluence/display/java/LCK03-J.+Do+not+synchronize+on+the+intrinsic+locks+of+high-level+concurrency+objects
//
// Every Java object has an intrinsic monitor, including a ReentrantLock, a
// ReadWriteLock or a concurrent collection. synchronized (lock) uses that
// monitor, which has nothing to do with the lock the object implements:
// a thread inside synchronized (lock) and a thread that called lock.lock()
// do not exclude each other at all. Mixing the two styles on one object
// leaves the data unprotected. Use the object's own API (lock()/unlock()
// in try/finally), and keep one locking style per piece of state.
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class LCK03_HighLevelLockObjects {
    private static final ReentrantLock LOCK = new ReentrantLock();

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // One component correctly holds the ReentrantLock during an update
        Thread owner = Thread.ofVirtual().start(() -> {
            LOCK.lock();
            try {
                held.countDown();
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                LOCK.unlock();
            }
        });
        held.await();

        System.out.println("noncompliant: entered while the lock was held? " + enteredNoncompliant());
        System.out.println("compliant:    entered while the lock was held? " + enteredCompliant());

        release.countDown();
        owner.join();
    }

    // Noncompliant: uses the ReentrantLock's monitor, not the lock itself
    static boolean enteredNoncompliant() throws InterruptedException {
        boolean[] entered = new boolean[1];
        Thread t = Thread.ofVirtual().start(() -> {
            synchronized (LOCK) {
                entered[0] = true;
            }
        });
        t.join();
        return entered[0];
    }

    // Compliant: uses the lock's own API (tryLock with a timeout for the demo)
    static boolean enteredCompliant() throws InterruptedException {
        boolean[] entered = new boolean[1];
        Thread t = Thread.ofVirtual().start(() -> {
            try {
                if (LOCK.tryLock(200, TimeUnit.MILLISECONDS)) {
                    try {
                        entered[0] = true;
                    } finally {
                        LOCK.unlock();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        t.join();
        return entered[0];
    }
}

/* Output:
noncompliant: entered while the lock was held? true
compliant:    entered while the lock was held? false
*/
