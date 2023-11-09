// Effective Java, Item 80: Prefer executors, tasks, and streams to threads.
//
// A Thread is both the unit of work and the mechanism that runs it. The
// Executor Framework separates the two: you describe work as tasks
// (Runnable, Callable) and hand them to an executor service that decides how
// to run them - a fixed pool, a cached pool, a fork-join pool, or (since
// Java 21) one virtual thread per task. You get waiting for results
// (invokeAll, invokeAny, Future), orderly shutdown and exception reporting
// for free, and you can change the execution policy without touching the
// tasks. Parallel streams use the fork-join pool under the hood.
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

public class ExecutorsTasksStreams {
    static long countPrimes(int from, int to) {
        return IntStream.range(from, to).filter(ExecutorsTasksStreams::isPrime).count();
    }

    static boolean isPrime(int n) {
        if (n < 2) {
            return false;
        }
        for (int d = 2; (long) d * d <= n; d++) {
            if (n % d == 0) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws Exception {
        int limit = 200_000, chunks = 8, step = limit / chunks;

        // Before: hand-managed threads and shared result slots
        long[] partial = new long[chunks];
        List<Thread> threads = new ArrayList<>();
        for (int c = 0; c < chunks; c++) {
            int idx = c;
            Thread t = new Thread(() -> partial[idx] = countPrimes(idx * step, (idx + 1) * step));
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
        long manual = 0;
        for (long p : partial) {
            manual += p;
        }
        System.out.println("raw threads:      " + manual + " primes below " + limit);

        // After: tasks + executor; the policy is one line and easy to swap
        List<Callable<Long>> tasks = new ArrayList<>();
        for (int c = 0; c < chunks; c++) {
            int from = c * step, to = (c + 1) * step;
            tasks.add(() -> countPrimes(from, to));
        }
        for (String policy : new String[] {"fixed pool", "virtual threads"}) {
            try (ExecutorService exec = policy.equals("fixed pool")
                    ? Executors.newFixedThreadPool(4)
                    : Executors.newVirtualThreadPerTaskExecutor()) {
                long total = 0;
                for (Future<Long> f : exec.invokeAll(tasks)) {
                    total += f.get();
                }
                System.out.printf("%-17s %d primes%n", policy + ":", total);
            }
        }

        // Failures come back through the Future instead of dying on a thread
        try (ExecutorService exec = Executors.newSingleThreadExecutor()) {
            Future<Integer> bad = exec.submit(() -> Integer.parseInt("not a number"));
            bad.get();
        } catch (ExecutionException e) {
            System.out.println("task failure surfaced: " + e.getCause().getClass().getSimpleName());
        }

        // Or skip explicit tasks: a parallel stream for a pure computation
        System.out.println("parallel stream:  " + IntStream.range(0, limit).parallel()
                .filter(ExecutorsTasksStreams::isPrime).count() + " primes");
    }
}

/* Output:
raw threads:      17984 primes below 200000
fixed pool:       17984 primes
virtual threads:  17984 primes
task failure surfaced: NumberFormatException
parallel stream:  17984 primes
*/
