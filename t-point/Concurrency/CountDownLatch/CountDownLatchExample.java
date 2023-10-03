// A CountDownLatch is a one-shot gate: it starts at a count, countDown()
// decrements it, and await() blocks until it reaches zero. Two classic uses:
//  - "start signal": workers await a latch of 1 so they all begin together
//  - "done signal": the coordinator awaits a latch of N that each worker
//    counts down when finished (like join() for tasks you did not create)
// await(timeout, unit) returns false instead of waiting forever, and a latch
// cannot be reset; use CyclicBarrier or Phaser for repeated rounds.
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CountDownLatchExample {
    public static void main(String[] args) throws InterruptedException {
        List<String> services = List.of("auth", "db", "cache", "audit-log");
        Map<String, String> status = new ConcurrentHashMap<>();

        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(services.size());

        ExecutorService pool = Executors.newFixedThreadPool(services.size());
        for (String service : services) {
            pool.execute(() -> {
                try {
                    startSignal.await(); // wait for the go
                    status.put(service, service.equals("cache") ? "DEGRADED" : "UP");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneSignal.countDown(); // always count down, even on failure
                }
            });
        }

        System.out.println("checks queued, finished so far: " + status.size());
        startSignal.countDown(); // release all workers at once
        boolean allReported = doneSignal.await(2, TimeUnit.SECONDS);
        System.out.println("all reported: " + allReported + ", remaining count: " + doneSignal.getCount());
        System.out.println("health: " + new TreeMap<>(status));

        // A latch that never reaches zero: await with a timeout gives up
        CountDownLatch quorum = new CountDownLatch(3);
        pool.execute(quorum::countDown); // only one of three approvals arrives
        boolean approved = quorum.await(100, TimeUnit.MILLISECONDS);
        System.out.println("quorum reached: " + approved + ", approvals still missing: " + quorum.getCount());

        // Counting down past zero is a no-op; the latch stays open
        doneSignal.countDown();
        System.out.println("count after extra countDown: " + doneSignal.getCount());

        pool.shutdown();
        System.out.println("terminated: " + pool.awaitTermination(2, TimeUnit.SECONDS));
    }
}

/* Output:
checks queued, finished so far: 0
all reported: true, remaining count: 0
health: {audit-log=UP, auth=UP, cache=DEGRADED, db=UP}
quorum reached: false, approvals still missing: 2
count after extra countDown: 0
terminated: true
*/
