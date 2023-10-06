// A CyclicBarrier makes a fixed number of threads (the parties) wait for each
// other at a common point, then releases them together and resets for the
// next round, which a CountDownLatch cannot do. An optional barrier action
// runs once per round, in the last thread to arrive, before anyone continues:
// a natural place to merge the round's partial results.
// If one party times out, is interrupted or the action throws, the barrier
// "breaks": every other waiter gets BrokenBarrierException.
// Here three workers each scan one slice of an address range per round, and
// the barrier action merges the round's findings before the next round starts.
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class CyclicBarrierExample {
    static final int WORKERS = 3;
    static final int ROUNDS = 3;
    static final int SLICE = 10; // hosts per worker per round

    // A host is "open" when its last octet is divisible by 7
    static boolean portOpen(int host) {
        return host % 7 == 0;
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicIntegerArray found = new AtomicIntegerArray(WORKERS); // per-worker hits this round
        AtomicInteger round = new AtomicInteger(1);
        List<String> report = new ArrayList<>();

        CyclicBarrier barrier = new CyclicBarrier(WORKERS, () -> {
            // Runs once per round, after all workers arrived
            int total = 0;
            for (int i = 0; i < WORKERS; i++) total += found.getAndSet(i, 0);
            report.add("round " + round.getAndIncrement() + ": " + total + " open hosts");
        });

        ExecutorService pool = Executors.newFixedThreadPool(WORKERS);
        for (int w = 0; w < WORKERS; w++) {
            int worker = w;
            pool.execute(() -> {
                try {
                    for (int r = 0; r < ROUNDS; r++) {
                        int start = r * WORKERS * SLICE + worker * SLICE;
                        for (int host = start; host < start + SLICE; host++) {
                            if (portOpen(host)) found.incrementAndGet(worker);
                        }
                        barrier.await(); // wait for the other slices of this round
                    }
                } catch (InterruptedException | BrokenBarrierException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        report.forEach(System.out::println);
        System.out.println("barrier reused for " + ROUNDS + " rounds, broken: " + barrier.isBroken());

        // A party that gives up breaks the barrier for everyone else
        CyclicBarrier pair = new CyclicBarrier(2);
        try {
            pair.await(100, TimeUnit.MILLISECONDS); // the partner never arrives
        } catch (TimeoutException e) {
            System.out.println("await timed out, broken: " + pair.isBroken());
        } catch (BrokenBarrierException e) {
            System.out.println("unexpected: " + e);
        }
        try {
            pair.await();
        } catch (BrokenBarrierException e) {
            System.out.println("next await: BrokenBarrierException");
        }
        pair.reset();
        System.out.println("after reset, broken: " + pair.isBroken() + ", waiting: " + pair.getNumberWaiting());
    }
}

/* Output:
round 1: 5 open hosts
round 2: 4 open hosts
round 3: 4 open hosts
barrier reused for 3 rounds, broken: false
await timed out, broken: true
next await: BrokenBarrierException
after reset, broken: false, waiting: 0
*/
