// Detecting deadlocks from inside the JVM with ThreadMXBean
//
// A deadlock is a cycle of threads, each waiting for a lock the next one
// holds; the classic cause is two code paths taking the same two locks in
// opposite order (CERT LCK07-J, ../../secure-coding/LCK/LCK07_LockOrdering.java,
// is about preventing it). The JVM knows who owns and who waits for every
// lock, so ThreadMXBean.findDeadlockedThreads() can find such cycles for
// both monitors (synchronized) and java.util.concurrent ownable locks; it is
// what jstack's "Found one Java-level deadlock" and many health checks use.
// Recovery is another matter: a thread blocked on a monitor cannot be
// interrupted, so a synchronized deadlock lasts until the JVM exits, while
// ReentrantLock.lockInterruptibly() or tryLock(timeout) leave a way out.
// Below, both kinds are created on purpose; latches make the deadlock
// certain, and every wait is bounded.
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class DeadlockDetection {
    static final ThreadMXBean MX = ManagementFactory.getThreadMXBean();

    public static void main(String[] args) throws Exception {
        // 1. ReentrantLock deadlock, recoverable through interruption
        ReentrantLock a = new ReentrantLock(), b = new ReentrantLock();
        CountDownLatch bothHoldFirst = new CountDownLatch(2);
        Thread t1 = new Thread(() -> lockBoth(a, b, bothHoldFirst), "transfer-1");
        Thread t2 = new Thread(() -> lockBoth(b, a, bothHoldFirst), "transfer-2");
        t1.start();
        t2.start();

        long[] ids = waitForDeadlock();
        System.out.println("lock deadlock found between: " + names(ids));
        ThreadInfo[] infos = MX.getThreadInfo(ids, true, true);
        Arrays.sort(infos, Comparator.comparing(ThreadInfo::getThreadName)); // id order is arbitrary
        for (ThreadInfo info : infos) {
            System.out.println("  " + info.getThreadName() + " waits for a lock owned by " + info.getLockOwnerName()
                    + " (state " + info.getThreadState() + ")");
        }
        t1.interrupt(); // lockInterruptibly() lets them give up
        t2.interrupt();
        t1.join(5_000);
        t2.join(5_000);
        System.out.println("after interrupting both: deadlock gone: " + (MX.findDeadlockedThreads() == null)
                + ", threads finished: " + (!t1.isAlive() && !t2.isAlive()));

        // 2. Monitor deadlock: detected the same way, but nothing can break it.
        //    Daemon threads, so the stuck pair does not keep the JVM alive.
        Object x = new Object(), y = new Object();
        CountDownLatch bothInside = new CountDownLatch(2);
        Thread m1 = daemon(() -> syncBoth(x, y, bothInside), "monitor-1");
        Thread m2 = daemon(() -> syncBoth(y, x, bothInside), "monitor-2");
        long[] monitorIds = waitForDeadlock();
        System.out.println("monitor deadlock found between: " + names(monitorIds)
                + ", also seen by findMonitorDeadlockedThreads: " + (MX.findMonitorDeadlockedThreads() != null));
        m1.interrupt();
        m2.interrupt();
        m1.join(500);
        System.out.println("interrupt helps a thread blocked on synchronized: " + !m1.isAlive()
                + " (state " + m1.getState() + ")");
    }

    static void lockBoth(ReentrantLock first, ReentrantLock second, CountDownLatch latch) {
        try {
            first.lockInterruptibly();
            try {
                latch.countDown();
                latch.await(5, TimeUnit.SECONDS); // both now hold their first lock
                second.lockInterruptibly();       // ...and wait for each other
                second.unlock();
            } finally {
                first.unlock();
            }
        } catch (InterruptedException e) {
            // give up: the finally block released the lock we held
        }
    }

    static void syncBoth(Object first, Object second, CountDownLatch latch) {
        synchronized (first) {
            latch.countDown();
            try {
                latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return;
            }
            synchronized (second) {
                System.out.println("unreachable");
            }
        }
    }

    static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        t.start();
        return t;
    }

    // Poll for at most 5 seconds; the latches make the deadlock certain, not instant
    static long[] waitForDeadlock() throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            long[] ids = MX.findDeadlockedThreads();
            if (ids != null && ids.length == 2) return ids;
            Thread.sleep(50);
        }
        throw new IllegalStateException("no deadlock detected");
    }

    static String names(long[] ids) {
        return Arrays.toString(Arrays.stream(MX.getThreadInfo(ids)).map(ThreadInfo::getThreadName).sorted().toArray());
    }
}

/* Output:
lock deadlock found between: [transfer-1, transfer-2]
  transfer-1 waits for a lock owned by transfer-2 (state WAITING)
  transfer-2 waits for a lock owned by transfer-1 (state WAITING)
after interrupting both: deadlock gone: true, threads finished: true
monitor deadlock found between: [monitor-1, monitor-2], also seen by findMonitorDeadlockedThreads: true
interrupt helps a thread blocked on synchronized: false (state BLOCKED)
*/
