// count++ is three steps (read, add, write), so two threads can read the same
// value and one increment is lost. The java.util.concurrent.atomic classes do
// such updates as a single lock-free step, built on compare-and-set (CAS):
// "set to new only if the current value is still expected".
//  - AtomicInteger/AtomicLong: incrementAndGet, addAndGet, updateAndGet,
//    accumulateAndGet, compareAndSet
//  - AtomicBoolean: a one-time flag ("only the first caller does X")
//  - AtomicReference: swap a whole immutable object atomically
//  - LongAdder: a counter split into cells; faster than AtomicLong under heavy
//    contention, but sum() is not a snapshot while writers are active
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

public class AtomicExample {
    static int plainCounter = 0; // racy on purpose

    record Policy(int version, Set<String> blockedCountries) {
        Policy block(String country) {
            var updated = new java.util.TreeSet<>(blockedCountries);
            updated.add(country);
            return new Policy(version + 1, Set.copyOf(updated));
        }
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicInteger atomicCounter = new AtomicInteger();
        LongAdder requests = new LongAdder();
        AtomicInteger largestPayload = new AtomicInteger();
        AtomicBoolean alertSent = new AtomicBoolean(false);
        AtomicInteger alertsSent = new AtomicInteger();

        int threads = 4, perThread = 50_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.execute(() -> {
                for (int i = 1; i <= perThread; i++) {
                    plainCounter++;                 // lost updates possible
                    atomicCounter.incrementAndGet(); // never loses one
                    requests.increment();
                    int payload = i % 1000;
                    largestPayload.accumulateAndGet(payload, Math::max);
                    // compareAndSet: exactly one thread wins the false -> true transition
                    if (i == perThread / 2 && alertSent.compareAndSet(false, true)) {
                        alertsSent.incrementAndGet();
                    }
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        int expected = threads * perThread;
        System.out.println("expected:        " + expected);
        System.out.println("AtomicInteger:   " + atomicCounter.get());
        System.out.println("LongAdder:       " + requests.sum());
        // The racy counter is usually lower, but how much lower varies per run
        System.out.println("plain int <= expected: " + (plainCounter <= expected));
        System.out.println("largest payload: " + largestPayload.get());
        System.out.println("alerts sent (4 threads raced to send): " + alertsSent.get());

        // compareAndSet by hand: the CAS retry loop that updateAndGet hides
        AtomicInteger tokens = new AtomicInteger(3);
        System.out.println("take token: " + tryTake(tokens) + ", " + tryTake(tokens) + ", "
                + tryTake(tokens) + ", " + tryTake(tokens) + " -> left " + tokens.get());

        // AtomicReference: readers always see a complete Policy, never a half-updated one
        AtomicReference<Policy> policy = new AtomicReference<>(new Policy(1, Set.of()));
        policy.updateAndGet(p -> p.block("KP"));
        policy.updateAndGet(p -> p.block("IR"));
        Policy current = policy.get();
        System.out.println("policy v" + current.version() + " blocks " + new java.util.TreeSet<>(current.blockedCountries()));
    }

    // Take one token if any are left, without locks
    static boolean tryTake(AtomicInteger tokens) {
        while (true) {
            int current = tokens.get();
            if (current == 0) return false;
            if (tokens.compareAndSet(current, current - 1)) return true;
            // another thread changed it between get and CAS: retry
        }
    }
}

/* Output:
expected:        200000
AtomicInteger:   200000
LongAdder:       200000
plain int <= expected: true
largest payload: 999
alerts sent (4 threads raced to send): 1
take token: true, true, true, false -> left 0
policy v3 blocks [IR, KP]
*/
