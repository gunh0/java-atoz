// CompletableFuture is a Future you can complete yourself and attach
// callbacks to, instead of blocking on get():
//  - supplyAsync(supplier, executor) / runAsync(runnable, executor) start work
//  - thenApply (transform), thenAccept (consume), thenRun (side effect) chain
//    stages; each returns a new CompletableFuture
//  - join() waits like get() but throws the unchecked CompletionException
//  - complete(value) / completeExceptionally(ex) finish a future by hand,
//    which turns callback-style APIs into futures
// Without an executor argument the async methods use the common ForkJoinPool;
// pass your own executor for blocking I/O.
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CompletableFutureExample {
    record Advisory(String id, double cvss) {}

    static Advisory fetchAdvisory(String id) {
        sleep(30); // pretend to call a remote feed
        return new Advisory(id, id.endsWith("1") ? 9.8 : 5.3);
    }

    static String severity(double cvss) {
        return cvss >= 9.0 ? "CRITICAL" : cvss >= 7.0 ? "HIGH" : cvss >= 4.0 ? "MEDIUM" : "LOW";
    }

    public static void main(String[] args) {
        try (ExecutorService io = Executors.newFixedThreadPool(2)) {
            // A pipeline of stages: fetch -> map -> format
            CompletableFuture<String> line = CompletableFuture
                    .supplyAsync(() -> fetchAdvisory("ADV-2023-001"), io)
                    .thenApply(a -> a.id() + " " + severity(a.cvss()));
            System.out.println("join(): " + line.join());

            // thenAccept / thenRun end a chain with a side effect
            List<String> log = Collections.synchronizedList(new ArrayList<>());
            CompletableFuture<Void> done = CompletableFuture
                    .supplyAsync(() -> fetchAdvisory("ADV-2023-002"), io)
                    .thenAccept(a -> log.add("stored " + a.id()))
                    .thenRun(() -> log.add("cache refreshed"));
            done.join();
            System.out.println("side effects in order: " + log);

            // An exception in any stage skips the rest and surfaces from join()
            CompletableFuture<String> broken = CompletableFuture
                    .supplyAsync(() -> fetchAdvisory("bad id"), io)
                    .thenApply(a -> { throw new IllegalArgumentException("malformed id " + a.id()); });
            try {
                broken.join();
            } catch (CompletionException e) {
                System.out.println("join() threw CompletionException caused by " + e.getCause());
            }
            System.out.println("isCompletedExceptionally: " + broken.isCompletedExceptionally());

            // Completing a future by hand, e.g. from a callback-based client
            CompletableFuture<String> reply = new CompletableFuture<>();
            CompletableFuture<Integer> length = reply.thenApply(String::length);
            io.execute(() -> reply.complete("pong"));
            System.out.println("manually completed: " + reply.join() + ", length stage: " + length.join());

            // complete() only wins once
            System.out.println("second complete() accepted: " + reply.complete("late"));
            // getNow returns a fallback while a future is still pending
            System.out.println("getNow on pending: " + new CompletableFuture<String>().getNow("pending"));
        } // close() waits for the executor's tasks, then shuts it down
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

/* Output:
join(): ADV-2023-001 CRITICAL
side effects in order: [stored ADV-2023-002, cache refreshed]
join() threw CompletionException caused by java.lang.IllegalArgumentException: malformed id bad id
isCompletedExceptionally: true
manually completed: pong, length stage: 4
second complete() accepted: false
getNow on pending: pending
*/
