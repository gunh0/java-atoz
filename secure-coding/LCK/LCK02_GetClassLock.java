// LCK02-J. Do not synchronize on the class object returned by getClass()
// https://wiki.sei.cmu.edu/confluence/display/java/LCK02-J.+Do+not+synchronize+on+the+class+object+returned+by+getClass()
//
// synchronized (getClass()) looks like a class-wide lock, but getClass()
// returns the RUNTIME class of the object. Called on a subclass instance it
// locks Subclass.class, not Base.class, so code in the base class that
// guards shared static state ends up using different locks depending on who
// calls it, and mutual exclusion silently disappears. Lock on an explicit
// class literal (Base.class) or, better, on a private static final lock
// object.
import java.util.concurrent.CountDownLatch;

public class LCK02_GetClassLock {
    public static void main(String[] args) throws InterruptedException {
        Counter base = new Counter();
        Counter sub = new AuditedCounter();
        System.out.println("lock used for a Counter:        " + base.lockNoncompliant().getSimpleName());
        System.out.println("lock used for an AuditedCounter: " + sub.lockNoncompliant().getSimpleName());

        // A Counter holds the class-wide lock in the middle of a slow update;
        // does an AuditedCounter wait for it?
        System.out.println("noncompliant: " + contend(base, sub, false));
        System.out.println("compliant:    " + contend(base, sub, true));
    }

    static String contend(Counter holder, Counter other, boolean compliant) throws InterruptedException {
        Counter.total = 0;
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread t1 = new Thread(() -> {
            if (compliant) holder.updateCompliant(inside, release);
            else holder.updateNoncompliant(inside, release);
        });
        t1.start();
        inside.await();

        Thread t2 = new Thread(() -> {
            if (compliant) other.updateCompliant(null, null);
            else other.updateNoncompliant(null, null);
        });
        t2.start();
        t2.join(300);
        String result = t2.isAlive() ? "the subclass instance waited for the shared lock"
                : "the subclass instance entered the critical section at the same time";
        release.countDown();
        t1.join();
        t2.join();
        return result + "; total after 2 increments: " + Counter.total;
    }
}

class Counter {
    private static final Object LOCK = new Object();
    static int total; // shared by every Counter and subclass instance

    Class<?> lockNoncompliant() {
        return getClass();
    }

    // Noncompliant: getClass() is AuditedCounter.class for subclass instances
    void updateNoncompliant(CountDownLatch inside, CountDownLatch release) {
        synchronized (getClass()) {
            slowIncrement(inside, release);
        }
    }

    // Compliant: one private lock, whatever the runtime class
    // (synchronized (Counter.class) would also be correct)
    void updateCompliant(CountDownLatch inside, CountDownLatch release) {
        synchronized (LOCK) {
            slowIncrement(inside, release);
        }
    }

    // A read-modify-write of total that the first thread pauses in the middle of
    private static void slowIncrement(CountDownLatch inside, CountDownLatch release) {
        int read = total;
        if (inside != null) {
            inside.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        total = read + 1;
    }
}

class AuditedCounter extends Counter {}

/* Output:
lock used for a Counter:        Counter
lock used for an AuditedCounter: AuditedCounter
noncompliant: the subclass instance entered the critical section at the same time; total after 2 increments: 1
compliant:    the subclass instance waited for the shared lock; total after 2 increments: 2
*/
