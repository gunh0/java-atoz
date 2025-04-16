// TPS01-J. Do not execute interdependent tasks in a bounded thread pool
// https://wiki.sei.cmu.edu/confluence/display/java/TPS01-J.+Do+not+execute+interdependent+tasks+in+a+bounded+thread+pool
//
// A task that submits sub-tasks to its OWN bounded pool and then blocks on
// their results holds a worker while it waits. Once every worker is held by
// a waiting parent, the children sit in the queue behind them and nobody
// makes progress: a thread-starvation deadlock. It needs no bug in any one
// task, only enough simultaneous requests, which makes it an easy
// denial-of-service: send as many slow requests as the pool has threads.
// Run dependent stages on a separate pool, or better, do not block at all:
// compose the stages (CompletableFuture.thenCombine) so a worker is never
// parked waiting for work queued behind it.
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TPS01_InterdependentTasks {
    static final int POOL_SIZE = 2;

    static String fetch(String part, int request) {
        return part + request;
    }

    // The demo's two latches only pin the timing (every worker is busy with a
    // parent before any child is queued, and no worker is freed before every
    // parent has given up), so the output does not depend on scheduling.
    static final CountDownLatch allParentsRunning = new CountDownLatch(POOL_SIZE);
    static final CountDownLatch allParentsGaveUp = new CountDownLatch(POOL_SIZE);

    // Noncompliant: the request handler fans out into the pool it runs on and blocks
    static String handleBlocking(ExecutorService pool, int request) throws Exception {
        allParentsRunning.countDown();
        allParentsRunning.await();
        Future<String> profile = pool.submit(() -> fetch("profile", request));
        Future<String> orders = pool.submit(() -> fetch("orders", request));
        try {
            // In production this is a plain get() and the request hangs forever
            return profile.get(300, TimeUnit.MILLISECONDS) + "+" + orders.get(300, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            allParentsGaveUp.countDown();
            allParentsGaveUp.await();
            return "request " + request + " stuck: its sub-tasks are queued behind it";
        }
    }

    // Compliant (1): sub-tasks run on a separate pool, so they can always make progress
    static String handleWithSeparatePool(ExecutorService ioPool, int request) throws Exception {
        Future<String> profile = ioPool.submit(() -> fetch("profile", request));
        Future<String> orders = ioPool.submit(() -> fetch("orders", request));
        return profile.get(300, TimeUnit.MILLISECONDS) + "+" + orders.get(300, TimeUnit.MILLISECONDS);
    }

    // Compliant (2): no worker ever blocks; the result is assembled when both parts are ready
    static CompletableFuture<String> handleAsync(ExecutorService pool, int request) {
        CompletableFuture<String> profile = CompletableFuture.supplyAsync(() -> fetch("profile", request), pool);
        CompletableFuture<String> orders = CompletableFuture.supplyAsync(() -> fetch("orders", request), pool);
        return profile.thenCombine(orders, (p, o) -> p + "+" + o);
    }

    public static void main(String[] args) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE)) {
            List<Future<String>> requests = new ArrayList<>();
            for (int r = 1; r <= POOL_SIZE; r++) { // as many concurrent requests as workers
                int request = r;
                requests.add(pool.submit(() -> handleBlocking(pool, request)));
            }
            System.out.println("noncompliant, sub-tasks in the same pool of " + POOL_SIZE + ":");
            for (Future<String> f : requests) System.out.println("  " + f.get());
        }

        try (ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE);
                ExecutorService ioPool = Executors.newFixedThreadPool(4)) {
            List<Future<String>> requests = new ArrayList<>();
            for (int r = 1; r <= POOL_SIZE; r++) {
                int request = r;
                requests.add(pool.submit(() -> handleWithSeparatePool(ioPool, request)));
            }
            System.out.println("compliant, sub-tasks in a separate pool:");
            for (Future<String> f : requests) System.out.println("  " + f.get());
        }

        try (ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE)) {
            List<CompletableFuture<String>> requests = new ArrayList<>();
            for (int r = 1; r <= 10; r++) { // even 5x more requests than workers
                requests.add(handleAsync(pool, r));
            }
            System.out.println("compliant, composed without blocking (10 requests, " + POOL_SIZE + " workers):");
            System.out.println("  " + requests.get(0).join() + " ... " + requests.get(9).join());
        }
    }
}

/* Output:
noncompliant, sub-tasks in the same pool of 2:
  request 1 stuck: its sub-tasks are queued behind it
  request 2 stuck: its sub-tasks are queued behind it
compliant, sub-tasks in a separate pool:
  profile1+orders1
  profile2+orders2
compliant, composed without blocking (10 requests, 2 workers):
  profile1+orders1 ... profile10+orders10
*/
