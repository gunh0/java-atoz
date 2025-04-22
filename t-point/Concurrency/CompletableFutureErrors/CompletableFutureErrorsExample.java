// How failures travel through a CompletableFuture pipeline, and the handlers
// that see them:
//  - an exception skips every following thenApply/thenAccept/thenCompose and
//    arrives at dependent stages wrapped in a CompletionException
//  - join() throws CompletionException, get() throws ExecutionException;
//    either way the real exception is getCause(), so unwrap before deciding
//  - whenComplete(r, ex) only OBSERVES (logging, metrics): the stage stays
//    failed. handle(r, ex) and exceptionally(ex) RECOVER with a value, and an
//    exception thrown inside them replaces the original one
//  - exceptionallyCompose(ex -> future) (Java 12) recovers with another
//    asynchronous call, e.g. a fallback service
//  - cancel() completes with CancellationException, which is not wrapped
//  - allOf fails if any input fails; Future.state() and exceptionNow()
//    (Java 19) report each input's outcome afterwards
// A failed future nobody looks at fails silently: always end a pipeline with
// a handler or a join.
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class CompletableFutureErrorsExample {
    static class QuoteServiceException extends RuntimeException {
        QuoteServiceException(String message) {
            super(message);
        }
    }

    static int primaryQuote() {
        throw new QuoteServiceException("primary quote service down");
    }

    static Throwable unwrap(Throwable t) {
        while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    static String describe(Throwable t) {
        Throwable root = unwrap(t);
        return (root == t ? "" : t.getClass().getSimpleName() + " -> ") + root.getClass().getSimpleName()
                + (root.getMessage() == null || root instanceof CancellationException ? "" : "(" + root.getMessage() + ")");
    }

    public static void main(String[] args) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            List<String> trace = new ArrayList<>();

            // 1. Propagation: later stages are skipped
            CompletableFuture<Integer> price = CompletableFuture
                    .supplyAsync(CompletableFutureErrorsExample::primaryQuote, pool)
                    .thenApply(p -> { trace.add("thenApply ran"); return p * 2; })
                    .whenComplete((p, ex) -> trace.add("whenComplete saw " + (ex == null ? "value " + p : describe(ex))));
            try {
                price.join();
            } catch (CompletionException e) {
                System.out.println("join(): " + describe(e));
            }
            try {
                price.get();
            } catch (ExecutionException e) {
                System.out.println("get():  " + describe(e));
            }
            System.out.println("trace: " + trace + ", still failed after whenComplete: " + price.isCompletedExceptionally());

            // 2. Recovering: exceptionally / handle
            System.out.println("exceptionally: " + price.exceptionally(ex -> -1).join());
            System.out.println("handle:        " + price.handle((p, ex) -> ex == null ? "price " + p : "fallback after " + unwrap(ex).getMessage()).join());

            // 3. A handler that throws replaces the error
            CompletableFuture<Integer> rethrown = price.exceptionally(ex -> {
                throw new IllegalStateException("cannot price order 42");
            });
            try {
                rethrown.join();
            } catch (CompletionException e) {
                System.out.println("handler threw: " + describe(e));
            }

            // 4. Recovering with another async call
            CompletableFuture<Integer> withFallback = price.exceptionallyCompose(ex ->
                    CompletableFuture.supplyAsync(() -> 95, pool)); // the backup quote service
            System.out.println("exceptionallyCompose: " + withFallback.join());

            // Recovery placement matters: a recovered value flows on through later stages
            int total = price.exceptionally(ex -> 50).thenApply(p -> p + 10).join();
            System.out.println("recover then continue: " + total);

            // 5. Cancellation is not wrapped
            CompletableFuture<String> never = new CompletableFuture<>();
            never.cancel(true);
            try {
                never.join();
            } catch (CancellationException e) {
                System.out.println("cancelled join(): " + describe(e) + ", isCancelled " + never.isCancelled());
            }
            CompletableFuture<String> dependent = never.thenApply(String::trim);
            try {
                dependent.join();
            } catch (CompletionException e) {
                System.out.println("dependent of cancelled: " + describe(e));
            }

            // 6. allOf and per-future outcomes
            List<CompletableFuture<String>> lookups = List.of(
                    CompletableFuture.supplyAsync(() -> "alice: ok", pool),
                    CompletableFuture.supplyAsync(() -> { throw new QuoteServiceException("bob: timeout"); }, pool),
                    CompletableFuture.supplyAsync(() -> "carol: ok", pool));
            CompletableFuture<Void> all = CompletableFuture.allOf(lookups.toArray(CompletableFuture[]::new));
            all.handle((v, ex) -> null).join(); // wait for all, failed or not
            System.out.println("allOf failed: " + all.isCompletedExceptionally());
            for (CompletableFuture<String> f : lookups) {
                System.out.println("  " + f.state() + ": " + (f.state() == Future.State.SUCCESS
                        ? f.resultNow() : unwrap(f.exceptionNow()).getMessage()));
            }

            // 7. Completing by hand: the first completion wins
            CompletableFuture<String> manual = new CompletableFuture<>();
            boolean failedFirst = manual.completeExceptionally(new QuoteServiceException("validation failed"));
            boolean thenValue = manual.complete("too late");
            System.out.println("completeExceptionally " + failedFirst + ", complete " + thenValue
                    + ", outcome: " + manual.handle((v, ex) -> ex == null ? v : unwrap(ex).getMessage()).join());
        }
    }
}

/* Output:
join(): CompletionException -> QuoteServiceException(primary quote service down)
get():  ExecutionException -> QuoteServiceException(primary quote service down)
trace: [whenComplete saw CompletionException -> QuoteServiceException(primary quote service down)], still failed after whenComplete: true
exceptionally: -1
handle:        fallback after primary quote service down
handler threw: CompletionException -> IllegalStateException(cannot price order 42)
exceptionallyCompose: 95
recover then continue: 60
cancelled join(): CancellationException, isCancelled true
dependent of cancelled: CompletionException -> CancellationException
allOf failed: true
  SUCCESS: alice: ok
  FAILED: bob: timeout
  SUCCESS: carol: ok
completeExceptionally true, complete false, outcome: validation failed
*/
