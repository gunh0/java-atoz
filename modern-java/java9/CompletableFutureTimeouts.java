// JEP 266 — CompletableFuture API enhancements (Java 9)
//
// Java 8's CompletableFuture had no way to give up on a stage: you could
// only block with get(timeout, unit) on the calling thread, which defeats
// the point of an asynchronous pipeline, or schedule your own watchdog on a
// ScheduledExecutorService. Java 9 added non-blocking timeouts:
// orTimeout() fails the future with TimeoutException, completeOnTimeout()
// completes it with a fallback value. It also added delayedExecutor() for
// "run this later", failedFuture()/completedStage() factories, and
// copy()/minimalCompletionStage() to hand out views that callers cannot
// complete behind your back.
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CompletableFutureTimeouts {
    public static void main(String[] args) throws Exception {
        // A "remote call" that never answers
        CompletableFuture<String> hanging = new CompletableFuture<>();

        // Before: a hand-made watchdog on a scheduler
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        CompletableFuture<String> guarded = new CompletableFuture<>();
        hanging.whenComplete((v, e) -> guarded.complete(v));
        scheduler.schedule(() -> guarded.completeExceptionally(new TimeoutException()), 100, TimeUnit.MILLISECONDS);
        try {
            guarded.get();
        } catch (ExecutionException e) {
            System.out.println("before: watchdog fired " + e.getCause().getClass().getSimpleName());
        }
        scheduler.shutdown();

        // After: orTimeout fails the stage, without blocking any thread while waiting
        String result = new CompletableFuture<String>()
                .orTimeout(100, TimeUnit.MILLISECONDS)
                .exceptionally(e -> "failed with " + unwrap(e).getClass().getSimpleName())
                .join();
        System.out.println("orTimeout: " + result);

        // completeOnTimeout supplies a fallback value instead
        String price = new CompletableFuture<String>()
                .completeOnTimeout("cached price 9.99", 100, TimeUnit.MILLISECONDS)
                .thenApply(String::toUpperCase)
                .join();
        System.out.println("completeOnTimeout: " + price);

        // A value that arrives in time wins over the timeout
        String fast = CompletableFuture.supplyAsync(() -> "fresh price 10.49")
                .completeOnTimeout("cached price 9.99", 5, TimeUnit.SECONDS)
                .join();
        System.out.println("in time: " + fast);

        // delayedExecutor: run something later without managing a scheduler
        Executor later = CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS);
        System.out.println("delayed: " + CompletableFuture.supplyAsync(() -> "ran after 50 ms", later).join());

        // failedFuture: a ready-made failed stage (handy in tests and fallbacks)
        CompletableFuture<Integer> failed = CompletableFuture.failedFuture(new IllegalStateException("no stock"));
        System.out.println("failedFuture: " + failed.handle((v, e) -> e.getMessage()).join());

        // minimalCompletionStage: callers can chain on it but cannot complete it
        CompletableFuture<String> owned = new CompletableFuture<>();
        java.util.concurrent.CompletionStage<String> view = owned.minimalCompletionStage();
        try {
            view.toCompletableFuture().complete("hijacked"); // completes a copy, not 'owned'
            ((CompletableFuture<String>) view).complete("hijacked");
        } catch (UnsupportedOperationException e) {
            System.out.println("minimal stage: complete() is unsupported");
        }
        owned.complete("owner's value");
        System.out.println("owner's future: " + owned.join());
    }

    static Throwable unwrap(Throwable e) {
        return e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
    }
}

/* Output:
before: watchdog fired TimeoutException
orTimeout: failed with TimeoutException
completeOnTimeout: CACHED PRICE 9.99
in time: fresh price 10.49
delayed: ran after 50 ms
failedFuture: no stock
minimal stage: complete() is unsupported
owner's future: owner's value
*/
