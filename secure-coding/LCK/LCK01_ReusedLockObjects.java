// LCK01-J. Do not synchronize on objects that may be reused
// https://wiki.sei.cmu.edu/confluence/display/java/LCK01-J.+Do+not+synchronize+on+objects+that+may+be+reused
//
// Some objects that look private are shared across the whole JVM: string
// literals and interned strings, Boolean.TRUE/FALSE, and boxed values from
// the Integer cache (-128..127, so Integer.valueOf(0) and autoboxed 0 are
// the same object everywhere). Two unrelated classes that both write
// synchronized ("lock") or synchronize on a boxed counter actually share one
// lock. One component can then stall the other, or the two can deadlock,
// and the cause is invisible in either class. Lock on a dedicated
// new Object() (private final), never on literals, boxed primitives or
// Boolean.
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

public class LCK01_ReusedLockObjects {
    public static void main(String[] args) throws InterruptedException {
        // The "different" locks are the same objects
        System.out.println("literal \"lock\" in two classes is one object: " + (Cache.lockObject() == Metrics.lockObject()));
        Integer a = 0, b = 0;
        System.out.println("autoboxed 0 in two places is one object:      " + (a == b));

        // A slow Cache operation blocks Metrics, which merely used the same literal
        System.out.println("noncompliant: " + contend(Cache::slowRefresh, Metrics::recordNoncompliant));
        System.out.println("compliant:    " + contend(Cache::slowRefresh, Metrics::recordCompliant));
    }

    // Thread 1 holds the Cache's lock until released; thread 2 calls into Metrics
    static String contend(Consumer<CountDownLatch[]> holder, Runnable other) throws InterruptedException {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread t1 = new Thread(() -> holder.accept(new CountDownLatch[] {locked, release}));
        t1.start();
        locked.await();

        Thread t2 = new Thread(other);
        t2.start();
        t2.join(500);
        String result = t2.isAlive() ? "Metrics.record() blocked by an unrelated Cache refresh"
                : "Metrics.record() ran independently (" + Metrics.count + " recorded)";
        release.countDown();
        t1.join();
        t2.join();
        return result;
    }
}

class Cache {
    static Object lockObject() {
        return "lock";
    }

    static void slowRefresh(CountDownLatch[] latches) {
        synchronized ("lock") { // noncompliant: a string literal is shared JVM-wide
            latches[0].countDown();
            try {
                latches[1].await(); // a long refresh
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

class Metrics {
    static int count;
    private static final Object LOCK = new Object(); // compliant: a dedicated lock

    static Object lockObject() {
        return "lock";
    }

    static void recordNoncompliant() {
        synchronized ("lock") {
            count++;
        }
    }

    static void recordCompliant() {
        synchronized (LOCK) {
            count++;
        }
    }
}

/* Output:
literal "lock" in two classes is one object: true
autoboxed 0 in two places is one object:      true
noncompliant: Metrics.record() blocked by an unrelated Cache refresh
compliant:    Metrics.record() ran independently (2 recorded)
*/
