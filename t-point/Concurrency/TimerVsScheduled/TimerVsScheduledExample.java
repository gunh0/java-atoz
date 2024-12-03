// java.util.Timer (Java 1.3) runs every TimerTask on ONE background thread.
// If any task throws, that thread dies, the Timer is cancelled for good and
// later schedule() calls fail with IllegalStateException: one bad task
// silently stops all the others. Timer also measures time with the wall clock.
// ScheduledExecutorService (Java 5) is the replacement: a pool of threads,
// tasks return ScheduledFutures, a failing periodic task only stops itself
// (its future completes exceptionally) and the rest keep running. It can also
// schedule Callables that return a value.
// This program waits on latches instead of sleeping, so its output does not
// depend on timing.
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TimerVsScheduledExample {
    public static void main(String[] args) throws Exception {
        timer();
        scheduledExecutor();
    }

    static void timer() throws InterruptedException {
        System.out.println("== java.util.Timer");
        CountDownLatch died = new CountDownLatch(1);
        // The Timer's thread is internal; catch what escapes from it here
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            System.out.println("timer thread died with " + e.getClass().getSimpleName());
            died.countDown();
        });

        Timer timer = new Timer("audit-timer", true);
        CountDownLatch ticks = new CountDownLatch(3);
        AtomicInteger heartbeat = new AtomicInteger();
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                heartbeat.incrementAndGet();
                ticks.countDown();
            }
        }, 0, 5);
        ticks.await();
        System.out.println("heartbeat ran at least 3 times: " + (heartbeat.get() >= 3));

        timer.schedule(new TimerTask() {
            public void run() {
                throw new IllegalArgumentException("bad input in one task");
            }
        }, 0);
        died.await();

        // The heartbeat task is gone too, and the Timer cannot be reused
        try {
            timer.schedule(new TimerTask() { public void run() { } }, 0);
        } catch (IllegalStateException e) {
            System.out.println("schedule after failure -> " + e.getClass().getSimpleName());
        }
        Thread.setDefaultUncaughtExceptionHandler(null);
    }

    static void scheduledExecutor() throws Exception {
        System.out.println("== ScheduledExecutorService");
        ScheduledExecutorService ses = Executors.newScheduledThreadPool(2);
        try {
            // A periodic task that fails on its third run
            AtomicInteger runs = new AtomicInteger();
            ScheduledFuture<?> flaky = ses.scheduleAtFixedRate(() -> {
                if (runs.incrementAndGet() == 3) {
                    throw new IllegalArgumentException("bad input in one task");
                }
            }, 0, 5, TimeUnit.MILLISECONDS);

            // A healthy periodic task on the same executor
            CountDownLatch healthy = new CountDownLatch(5);
            ScheduledFuture<?> heartbeat = ses.scheduleWithFixedDelay(healthy::countDown, 0, 5, TimeUnit.MILLISECONDS);

            try {
                flaky.get(); // a periodic future only completes when the task fails or is cancelled
            } catch (ExecutionException e) {
                System.out.println("flaky task failed with " + e.getCause().getClass().getSimpleName()
                        + " after " + runs.get() + " runs");
            }
            healthy.await();
            System.out.println("heartbeat still running after the failure: " + !heartbeat.isDone());
            heartbeat.cancel(false);
            System.out.println("heartbeat cancelled: " + heartbeat.isCancelled());

            // Delayed Callable with a result
            ScheduledFuture<String> report = ses.schedule(() -> "report ready", 20, TimeUnit.MILLISECONDS);
            System.out.println("callable result: " + report.get(5, TimeUnit.SECONDS));
            System.out.println("flaky runs did not continue: " + (runs.get() == 3));
        } finally {
            ses.shutdown();
            System.out.println("terminated: " + ses.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}

/* Output:
== java.util.Timer
heartbeat ran at least 3 times: true
timer thread died with IllegalArgumentException
schedule after failure -> IllegalStateException
== ScheduledExecutorService
flaky task failed with IllegalArgumentException after 3 runs
heartbeat still running after the failure: true
heartbeat cancelled: true
callable result: report ready
flaky runs did not continue: true
terminated: true
*/
