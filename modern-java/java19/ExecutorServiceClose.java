// Java 19 — ExecutorService extends AutoCloseable (API addition, no JEP)
//
// Shutting an executor down correctly took a ritual: call shutdown(), loop
// on awaitTermination(), handle InterruptedException by calling
// shutdownNow() and re-interrupting, all in a finally block. Forgetting it
// leaks threads, and non-daemon pool threads keep the JVM alive. Since
// Java 19 ExecutorService is AutoCloseable: its default close() does that
// ritual (shutdown, wait for submitted tasks to finish, cancel them if the
// waiting thread is interrupted), so a try-with-resources block now bounds
// the executor's lifetime.
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ExecutorServiceClose {
    public static void main(String[] args) throws Exception {
        // Before: explicit shutdown and termination handling
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 1; i <= 8; i++) {
                int n = i;
                results.add(pool.submit(() -> n * n));
            }
        } finally {
            pool.shutdown();
            try {
                while (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
                    // keep waiting
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        int sum = 0;
        for (Future<Integer> f : results) {
            sum += f.get();
        }
        System.out.println("before: sum of squares = " + sum + ", terminated = " + pool.isTerminated());

        // After: try-with-resources; close() waits for all submitted tasks
        AtomicInteger done = new AtomicInteger();
        ExecutorService escaped;
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            escaped = executor;
            for (int i = 0; i < 8; i++) {
                executor.submit(() -> {
                    Thread.sleep(50);
                    return done.incrementAndGet();
                });
            }
        } // implicit close(): blocks until all 8 tasks are finished
        System.out.println("after: tasks completed when the block ends = " + done.get()
                + ", terminated = " + escaped.isTerminated());

        // A closed executor rejects new work, like after shutdown()
        try {
            escaped.submit(() -> 1);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            System.out.println("submit after close: RejectedExecutionException");
        }

        // Calling close() again is harmless
        escaped.close();
        System.out.println("second close(): no-op");
    }
}

/* Output:
before: sum of squares = 204, terminated = true
after: tasks completed when the block ends = 8, terminated = true
submit after close: RejectedExecutionException
second close(): no-op
*/
