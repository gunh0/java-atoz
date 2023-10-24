// JEP 444 — Virtual Threads, part 2: one virtual thread per task (Java 21)
//
// With platform threads, an ExecutorService is a POOL: threads are
// expensive, so a fixed number of them is shared and tasks queue up behind
// each other. Sizing that pool for blocking work is guesswork: too small
// and requests wait in the queue, too large and the OS runs out of
// threads. Virtual threads are cheap, so pooling them is pointless:
// Executors.newVirtualThreadPerTaskExecutor() starts a new virtual thread
// for every submitted task, and newThreadPerTaskExecutor(factory) does the
// same with a named Thread.Builder factory. Combined with try-with-resources
// (ExecutorService is AutoCloseable since Java 19) the block cannot exit
// until every task has finished: a simple form of structured concurrency.
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

public class VirtualThreadPerTaskExecutor {
    static final int TASKS = 200;

    // A request handler that spends its time blocked, e.g. on a database call
    static int handle(int id) throws InterruptedException {
        Thread.sleep(Duration.ofMillis(50));
        return id * 2;
    }

    public static void main(String[] args) throws Exception {
        List<Callable<Integer>> work = IntStream.range(0, TASKS)
                .<Callable<Integer>>mapToObj(id -> () -> handle(id))
                .toList();

        // Before: a pool of 10 platform threads -> 200 tasks run in 20 waves of 50 ms
        long start = System.nanoTime();
        int sum;
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            sum = sumOf(pool.invokeAll(work));
        }
        long pooled = elapsedMillis(start);
        System.out.println("fixed pool of 10: sum " + sum + ", needed at least 20 waves (>= 1 s): " + (pooled >= 1000));

        // After: every task gets its own virtual thread -> all 200 block at the same time
        start = System.nanoTime();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            sum = sumOf(executor.invokeAll(work));
        }
        long perTask = elapsedMillis(start);
        System.out.println("virtual per task: sum " + sum + ", finished in well under 1 s: " + (perTask < 1000));

        // Named virtual threads through a builder-made factory
        try (ExecutorService named = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("request-", 1).factory())) {
            Future<String> who = named.submit(() -> Thread.currentThread().getName()
                    + " virtual=" + Thread.currentThread().isVirtual());
            System.out.println("named: " + who.get());
        }

        // The try block is the lifetime of the tasks: close() waits for all of them
        List<Future<Integer>> futures;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            futures = IntStream.range(0, 5).mapToObj(id -> executor.submit(() -> handle(id))).toList();
        }
        System.out.println("after the block, states: " + futures.stream().map(Future::state).toList()
                + ", results " + futures.stream().map(Future::resultNow).toList());

        // A failing task does not hide: its Future carries the exception
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Integer> bad = executor.submit(() -> {
                throw new IllegalStateException("upstream timeout");
            });
            executor.close(); // explicit close is the same as leaving the block
            System.out.println("failed task: " + bad.state() + ", " + bad.exceptionNow().getMessage());
        }
    }

    static int sumOf(List<Future<Integer>> futures) {
        return futures.stream().mapToInt(Future::resultNow).sum();
    }

    static long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}

/* Output:
fixed pool of 10: sum 39800, needed at least 20 waves (>= 1 s): true
virtual per task: sum 39800, finished in well under 1 s: true
named: request-1 virtual=true
after the block, states: [SUCCESS, SUCCESS, SUCCESS, SUCCESS, SUCCESS], results [0, 2, 4, 6, 8]
failed task: FAILED, upstream timeout
*/
