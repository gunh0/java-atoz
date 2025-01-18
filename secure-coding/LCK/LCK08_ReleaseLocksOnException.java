// LCK08-J. Ensure actively held locks are released on exceptional conditions
// https://wiki.sei.cmu.edu/confluence/display/java/LCK08-J.+Ensure+actively+held+locks+are+released+on+exceptional+conditions
//
// An intrinsic lock (synchronized) is released automatically when the block
// is left by an exception. A java.util.concurrent.locks.Lock is not: if the
// code between lock() and unlock() throws, the lock stays held by a thread
// that has moved on, and every later caller blocks forever. One malformed
// request is then enough to take the service down (denial of service).
// Call lock() immediately BEFORE a try block and unlock() in its finally.
// Do not call lock() inside the try: if lock() itself fails, the finally
// would unlock a lock this thread never acquired.
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class LCK08_ReleaseLocksOnException {
    static class QuotaStore {
        private final ReentrantLock lock = new ReentrantLock();
        private int quota = 100;

        // Noncompliant: unlock() is skipped when parseInt throws
        void setQuotaNoncompliant(String input) {
            lock.lock();
            quota = Integer.parseInt(input);
            lock.unlock();
        }

        // Compliant: lock() before try, unlock() in finally
        void setQuotaCompliant(String input) {
            lock.lock();
            try {
                quota = Integer.parseInt(input);
            } finally {
                lock.unlock();
            }
        }

        // Compliant alternative: an intrinsic lock is released by the JVM on any exit
        synchronized void setQuotaSynchronized(String input) {
            quota = Integer.parseInt(input);
        }

        // What every other request does: needs the lock to read the quota
        String readQuota() throws InterruptedException {
            if (!lock.tryLock(200, TimeUnit.MILLISECONDS)) {
                return "blocked (lock still held: " + lock.isLocked() + ")";
            }
            try {
                return "quota " + quota;
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        for (String mode : new String[] {"noncompliant", "compliant"}) {
            QuotaStore store = new QuotaStore();
            // One bad request on a worker thread that then ends
            Thread worker = new Thread(() -> {
                try {
                    if (mode.equals("noncompliant")) {
                        store.setQuotaNoncompliant("12x");
                    } else {
                        store.setQuotaCompliant("12x");
                    }
                } catch (NumberFormatException e) {
                    System.out.println(mode + ": bad request rejected with " + e.getClass().getSimpleName());
                }
            });
            worker.start();
            worker.join();
            // The next, perfectly valid request on another thread
            String[] next = new String[1];
            Thread reader = new Thread(() -> {
                try {
                    next[0] = store.readQuota();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            reader.start();
            reader.join();
            System.out.println(mode + ": next request -> " + next[0]);
        }

        QuotaStore intrinsic = new QuotaStore();
        Thread bad = new Thread(() -> {
            try {
                intrinsic.setQuotaSynchronized("12x");
            } catch (NumberFormatException e) {
                System.out.println("synchronized: bad request rejected with " + e.getClass().getSimpleName());
            }
        });
        bad.start();
        bad.join();
        // Another thread can enter the monitor right away
        Thread good = new Thread(() -> intrinsic.setQuotaSynchronized("250"));
        good.start();
        good.join(2_000);
        System.out.println("synchronized: next request finished: " + !good.isAlive()
                + ", " + intrinsic.readQuota());
    }
}

/* Output:
noncompliant: bad request rejected with NumberFormatException
noncompliant: next request -> blocked (lock still held: true)
compliant: bad request rejected with NumberFormatException
compliant: next request -> quota 100
synchronized: bad request rejected with NumberFormatException
synchronized: next request finished: true, quota 250
*/
