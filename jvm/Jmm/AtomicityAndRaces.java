// Atomicity: why count++ loses updates, and the tools that do not
//
// count++ is three steps (read, add, write). Two threads can both read 41
// and both write 42: one increment is lost. Neither visibility (volatile)
// nor luck fixes that; you need an atomic read-modify-write (AtomicInteger's
// compare-and-set loop, LongAdder's striped cells for heavy contention) or
// mutual exclusion (synchronized, ReentrantLock). The JMM adds one more
// atomicity rule: reads and writes of int, references and other 32-bit
// values are atomic, but plain long and double writes may be split into two
// 32-bit halves (word tearing, JLS 17.7) unless the field is volatile; 64-bit
// HotSpot does not tear, but the language does not promise it. How MANY
// updates the racy counter loses depends on scheduling, so the program
// prints only verdicts that hold on every run.
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class AtomicityAndRaces {
    static final int THREADS = 4, PER_THREAD = 250_000, EXPECTED = THREADS * PER_THREAD;

    static int racy;
    static volatile int volatileRacy;
    static int locked;
    static final Object LOCK = new Object();
    static final AtomicInteger atomic = new AtomicInteger();
    static final LongAdder adder = new LongAdder();

    public static void main(String[] args) throws Exception {
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            Thread th = new Thread(() -> {
                for (int i = 0; i < PER_THREAD; i++) {
                    racy++;                                   // data race
                    volatileRacy++;                           // visible, still not atomic
                    synchronized (LOCK) { locked++; }         // mutual exclusion
                    atomic.incrementAndGet();                 // CAS
                    adder.increment();                        // striped counter
                }
            });
            threads.add(th);
            th.start();
        }
        for (Thread th : threads) th.join();                  // join: all results visible now

        System.out.println("expected " + EXPECTED + " increments");
        System.out.println("plain int:     never more than expected: " + (racy <= EXPECTED) + " (lost updates are possible)");
        System.out.println("volatile int:  never more than expected: " + (volatileRacy <= EXPECTED) + " (lost updates are possible)");
        System.out.println("synchronized:  exact: " + (locked == EXPECTED));
        System.out.println("AtomicInteger: exact: " + (atomic.get() == EXPECTED));
        System.out.println("LongAdder:     exact: " + (adder.sum() == EXPECTED));

        // compareAndSet is the primitive behind atomic updates
        AtomicInteger v = new AtomicInteger(5);
        System.out.println("CAS 5->6 succeeds: " + v.compareAndSet(5, 6) + ", CAS 5->7 fails: " + !v.compareAndSet(5, 7) + ", value " + v.get());
        // updateAndGet retries the function until its CAS wins
        System.out.println("updateAndGet(x -> x * 10): " + v.updateAndGet(x -> x * 10));
    }
}

/* Output:
expected 1000000 increments
plain int:     never more than expected: true (lost updates are possible)
volatile int:  never more than expected: true (lost updates are possible)
synchronized:  exact: true
AtomicInteger: exact: true
LongAdder:     exact: true
CAS 5->6 succeeds: true, CAS 5->7 fails: true, value 6
updateAndGet(x -> x * 10): 60
*/
