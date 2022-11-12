// Java 19 — Future.state, Future.resultNow and Future.exceptionNow
// (API additions, no JEP)
//
// To read a Future that you already know is complete (after invokeAll, or
// while filtering a list of finished tasks), Future.get() still forces you
// to handle InterruptedException and ExecutionException, and isDone() cannot
// tell success from failure or cancellation. Java 19 adds state(), returning
// RUNNING, SUCCESS, FAILED or CANCELLED, and two non-blocking accessors:
// resultNow() returns the result of a successful task and exceptionNow()
// the exception of a failed one; both throw IllegalStateException if the
// task is in any other state. They are default methods, so every Future
// implementation (FutureTask, CompletableFuture, ...) has them.
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.stream.Collectors;

public class FutureStateResultNow {
    public static void main(String[] args) {
        // A FutureTask run synchronously, plus CompletableFutures in every state
        FutureTask<Integer> computed = new FutureTask<>(() -> 6 * 7);
        computed.run();
        CompletableFuture<Integer> failed = CompletableFuture.failedFuture(new IllegalArgumentException("bad input"));
        CompletableFuture<Integer> pending = new CompletableFuture<>();
        CompletableFuture<Integer> cancelled = new CompletableFuture<>();
        cancelled.cancel(true);
        List<Future<Integer>> futures = List.of(computed, failed, pending, cancelled);

        // Before: isDone() is true for three of them, and get() needs try/catch
        int sumBefore = 0;
        for (Future<Integer> f : futures) {
            if (f.isDone() && !f.isCancelled()) {
                try {
                    sumBefore += f.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    // failed task: skip
                }
            }
        }
        System.out.println("before: sum of successful results = " + sumBefore);

        // After: switch on the state, read results without blocking or checked exceptions
        for (Future<Integer> f : futures) {
            String detail = switch (f.state()) {
                case SUCCESS -> "result " + f.resultNow();
                case FAILED -> "exception " + f.exceptionNow();
                case CANCELLED -> "cancelled";
                case RUNNING -> "not finished yet";
            };
            System.out.printf("%-21s %-9s %s%n", f.getClass().getSimpleName(), f.state(), detail);
        }

        int sumAfter = futures.stream()
                .filter(f -> f.state() == Future.State.SUCCESS)
                .mapToInt(Future::resultNow)
                .sum();
        System.out.println("after: sum of successful results = " + sumAfter);

        List<String> errors = futures.stream()
                .filter(f -> f.state() == Future.State.FAILED)
                .map(f -> f.exceptionNow().getMessage())
                .collect(Collectors.toList());
        System.out.println("after: errors = " + errors);

        // Asking for the wrong thing is a programming error, reported at once
        try {
            pending.resultNow();
        } catch (IllegalStateException e) {
            System.out.println("resultNow() on a running task: IllegalStateException");
        }
    }
}

/* Output:
before: sum of successful results = 42
FutureTask            SUCCESS   result 42
CompletableFuture     FAILED    exception java.lang.IllegalArgumentException: bad input
CompletableFuture     RUNNING   not finished yet
CompletableFuture     CANCELLED cancelled
after: sum of successful results = 42
after: errors = [bad input]
resultNow() on a running task: IllegalStateException
*/
