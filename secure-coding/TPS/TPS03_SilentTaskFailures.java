// TPS03-J. Ensure that tasks executing in a thread pool do not fail silently
// https://wiki.sei.cmu.edu/confluence/display/java/TPS03-J.+Ensure+that+tasks+executing+in+a+thread+pool+do+not+fail+silently
//
// ExecutorService.submit wraps the task in a Future. If the task throws,
// the exception is stored in that Future and nothing else happens: no
// stack trace, no uncaught-exception handler, no log line. When nobody
// calls get(), a failed security task (rotating a key, revoking sessions,
// writing an audit record) simply did not happen and no one knows. Always
// observe the outcome: call Future.get() and handle ExecutionException,
// override ThreadPoolExecutor.afterExecute, or catch and report inside the
// task itself.
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TPS03_SilentTaskFailures {
    static final List<String> REPORTED = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) throws Exception {
        Runnable revokeSessions = () -> {
            throw new IllegalStateException("session store unreachable");
        };

        // A JVM-wide safety net: would catch exceptions that escape a thread
        List<String> uncaught = Collections.synchronizedList(new ArrayList<>());
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> uncaught.add(e.getMessage()));

        // Noncompliant: submit() and forget
        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(revokeSessions);
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("noncompliant: task failed, uncaught-exception handler calls: " + uncaught.size()
                + ", nothing printed");

        // Compliant 1: look at the Future
        ExecutorService pool2 = Executors.newFixedThreadPool(2);
        Future<?> f = pool2.submit(revokeSessions);
        try {
            f.get();
        } catch (ExecutionException e) {
            System.out.println("compliant Future.get():    " + e.getCause().getClass().getSimpleName()
                    + ": " + e.getCause().getMessage());
        } finally {
            pool2.shutdown();
        }

        // Compliant 2: an executor that reports every failed task
        ThreadPoolExecutor pool3 = new ReportingExecutor(2);
        pool3.submit(revokeSessions);
        pool3.execute(revokeSessions);
        pool3.shutdown();
        pool3.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("compliant afterExecute:    " + REPORTED.size() + " failures reported");
        REPORTED.stream().sorted().forEach(r -> System.out.println("  " + r));
    }
}

class ReportingExecutor extends ThreadPoolExecutor {
    ReportingExecutor(int threads) {
        super(threads, threads, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r);
                    t.setUncaughtExceptionHandler((th, e) -> { }); // reported in afterExecute already
                    return t;
                });
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        // execute(): t is the exception; submit(): it is inside the completed Future
        if (t == null && r instanceof Future<?> future && future.isDone()) {
            try {
                future.get();
            } catch (ExecutionException e) {
                t = e.getCause();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.util.concurrent.CancellationException e) {
                t = e;
            }
        }
        if (t != null) {
            TPS03_SilentTaskFailures.REPORTED.add((r instanceof Future<?> ? "submit: " : "execute: ")
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }
}

/* Output:
noncompliant: task failed, uncaught-exception handler calls: 0, nothing printed
compliant Future.get():    IllegalStateException: session store unreachable
compliant afterExecute:    2 failures reported
  execute: IllegalStateException: session store unreachable
  submit: IllegalStateException: session store unreachable
*/
