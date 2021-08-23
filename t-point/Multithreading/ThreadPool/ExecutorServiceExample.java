// A thread pool reuses a fixed set of worker threads for many tasks instead of
// creating a new Thread per task (thread creation is expensive and unbounded
// thread counts exhaust memory). ExecutorService is the java.util.concurrent
// interface for this:
//  - submit(Runnable/Callable) queues a task and returns a Future for its result
//  - invokeAll(tasks) runs a batch and waits for all of them
//  - shutdown() stops accepting tasks; awaitTermination() waits for the rest.
//    A pool that is never shut down keeps the JVM alive.
// Tasks may run in any order on any worker, so collect results through the
// Futures (in submission order) rather than printing from inside the tasks.
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ExecutorServiceExample {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        Set<String> workerNames = ConcurrentHashMap.newKeySet();
        try {
            // submit(): one Future per task
            List<Future<Integer>> futures = new ArrayList<>();
            for (int n = 1; n <= 8; n++) {
                int number = n;
                futures.add(pool.submit(() -> {
                    workerNames.add(Thread.currentThread().getName());
                    Thread.sleep(20); // simulate work
                    return number * number;
                }));
            }
            List<Integer> squares = new ArrayList<>();
            for (Future<Integer> f : futures) {
                squares.add(f.get()); // blocks until that task is done
            }
            System.out.println("squares: " + squares);

            // invokeAll(): run a batch of Callables and wait for all
            List<Callable<String>> jobs = List.of(
                    () -> "report-A done",
                    () -> "report-B done",
                    () -> "report-C done");
            for (Future<String> f : pool.invokeAll(jobs)) {
                System.out.println(f.get());
            }

            // A task that throws: the exception is wrapped in ExecutionException
            Future<Integer> failing = pool.submit(() -> Integer.parseInt("oops"));
            try {
                failing.get();
            } catch (ExecutionException e) {
                System.out.println("task failed: " + e.getCause().getClass().getSimpleName());
            }
        } finally {
            pool.shutdown();
        }
        System.out.println("terminated: " + pool.awaitTermination(5, TimeUnit.SECONDS));
        System.out.println("8 square tasks shared at most 3 threads: " + (workerNames.size() <= 3));

        try {
            pool.submit(() -> "too late");
        } catch (java.util.concurrent.RejectedExecutionException e) {
            System.out.println("submit after shutdown: RejectedExecutionException");
        }
    }
}

/* Output:
squares: [1, 4, 9, 16, 25, 36, 49, 64]
report-A done
report-B done
report-C done
task failed: NumberFormatException
terminated: true
8 square tasks shared at most 3 threads: true
submit after shutdown: RejectedExecutionException
*/
