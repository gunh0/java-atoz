// A Semaphore holds a number of permits. acquire() takes one (blocking while
// none are left) and release() returns it, so at most N threads are inside
// the guarded section at once: a concurrency limit, e.g. "no more than 3
// simultaneous connections to the license server".
//  - tryAcquire() takes a permit only if one is free right now (or within a
//    timeout), which suits "reject instead of queue" throttling
//  - always release in finally; permits are not tied to a thread, so a
//    missing release leaks capacity forever
// Virtual threads (Java 21) are cheap to block, so one thread per request plus
// a semaphore is a simple way to cap load on a downstream system.
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class SemaphoreExample {
    static final Semaphore connections = new Semaphore(3);
    static final AtomicInteger inFlight = new AtomicInteger();
    static final AtomicInteger maxInFlight = new AtomicInteger();

    static String queryLicenseServer(int request) throws InterruptedException {
        connections.acquire();
        try {
            int now = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(now, Math::max);
            Thread.sleep(10); // the slow remote call
            return "license-" + request;
        } finally {
            inFlight.decrementAndGet();
            connections.release();
        }
    }

    public static void main(String[] args) throws Exception {
        List<Future<String>> results = new ArrayList<>();
        try (ExecutorService perRequest = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 1; i <= 60; i++) {
                int request = i;
                results.add(perRequest.submit(() -> queryLicenseServer(request)));
            }
        } // close() waits for all 60 tasks
        System.out.println("requests served: " + results.size());
        System.out.println("first and last: " + results.get(0).get() + ", " + results.get(59).get());
        System.out.println("max concurrent calls: " + maxInFlight.get());
        System.out.println("permits back: " + connections.availablePermits());

        // tryAcquire: shed load instead of queueing
        Semaphore loginSlots = new Semaphore(2);
        List<String> outcomes = new ArrayList<>();
        for (String user : List.of("alice", "bob", "carol")) {
            outcomes.add(user + (loginSlots.tryAcquire() ? " accepted" : " rejected (busy)"));
        }
        System.out.println(outcomes);
        loginSlots.release();
        System.out.println("after one release, dave " + (loginSlots.tryAcquire() ? "accepted" : "rejected"));

        // acquire(n) / tryAcquire(n) take several permits at once, e.g. for a heavy job
        Semaphore budget = new Semaphore(5);
        System.out.println("heavy job (4 permits) admitted: " + budget.tryAcquire(4)
                + ", second heavy job admitted: " + budget.tryAcquire(4)
                + ", left: " + budget.availablePermits());
    }
}

/* Output:
requests served: 60
first and last: license-1, license-60
max concurrent calls: 3
permits back: 3
[alice accepted, bob accepted, carol rejected (busy)]
after one release, dave accepted
heavy job (4 permits) admitted: true, second heavy job admitted: false, left: 1
*/
