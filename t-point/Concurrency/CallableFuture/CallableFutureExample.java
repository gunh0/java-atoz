// Callable<V> is a task that returns a value and may throw a checked
// exception (Runnable can do neither). Submitting one to an executor gives a
// Future<V>, a handle on the pending result:
//  - get() blocks; get(timeout, unit) gives up with TimeoutException
//  - cancel(true) interrupts a running task; get() then throws
//    CancellationException
//  - isDone()/isCancelled() poll without blocking; state() and resultNow()
//    (Java 19) read a finished Future without try/catch noise
//  - invokeAny() returns the first task that completes successfully and
//    cancels the rest: handy for "ask several mirrors, take the first answer"
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CallableFutureExample {
    // A Callable may declare checked exceptions; a Runnable may not
    static Callable<Integer> lookupPort(String service) {
        return () -> switch (service) {
            case "https" -> 443;
            case "ssh" -> 22;
            default -> throw new java.io.IOException("unknown service " + service);
        };
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<Integer> https = pool.submit(lookupPort("https"));
            System.out.println("https -> " + https.get());

            Future<Integer> bogus = pool.submit(lookupPort("gopher"));
            try {
                bogus.get();
            } catch (ExecutionException e) {
                // The checked exception travels inside ExecutionException
                System.out.println("gopher -> " + e.getCause());
            }
            System.out.println("state of failed future: " + bogus.state());

            // get with a timeout: the caller stops waiting, the task keeps running
            CountDownLatch never = new CountDownLatch(1);
            Future<String> slowScan = pool.submit(() -> {
                never.await(); // a scan that would hang forever
                return "scan finished";
            });
            try {
                slowScan.get(100, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                System.out.println("slow scan: TimeoutException after 100 ms, done=" + slowScan.isDone());
            }

            // cancel(true) interrupts the worker thread blocked in await()
            boolean cancelled = slowScan.cancel(true);
            System.out.println("cancel(true) -> " + cancelled + ", isCancelled=" + slowScan.isCancelled()
                    + ", isDone=" + slowScan.isDone() + ", state=" + slowScan.state());
            try {
                slowScan.get();
            } catch (CancellationException e) {
                System.out.println("get() on cancelled future: CancellationException");
            }

            // resultNow() for futures known to be complete
            Future<Integer> ssh = pool.submit(lookupPort("ssh"));
            ssh.get();
            System.out.println("ssh resultNow() -> " + ssh.resultNow());

            // invokeAny: first successful result wins, failures are ignored
            List<Callable<String>> mirrors = List.of(
                    () -> { throw new IllegalStateException("mirror-1 down"); },
                    () -> { Thread.sleep(300); return "mirror-2 (slow)"; },
                    () -> { Thread.sleep(20); return "mirror-3"; });
            System.out.println("invokeAny -> " + pool.invokeAny(mirrors));
        } finally {
            pool.shutdownNow();
        }
        System.out.println("terminated: " + pool.awaitTermination(5, TimeUnit.SECONDS));
    }
}

/* Output:
https -> 443
gopher -> java.io.IOException: unknown service gopher
state of failed future: FAILED
slow scan: TimeoutException after 100 ms, done=false
cancel(true) -> true, isCancelled=true, isDone=true, state=CANCELLED
get() on cancelled future: CancellationException
ssh resultNow() -> 22
invokeAny -> mirror-3
terminated: true
*/
