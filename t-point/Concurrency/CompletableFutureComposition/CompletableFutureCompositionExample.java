// Composing CompletableFutures without blocking in between:
//  - thenCompose(f -> nextFuture) chains a dependent async call (flatMap);
//    thenApply would give a CompletableFuture<CompletableFuture<T>>
//  - thenCombine(other, fn) joins two independent futures when both finish
//  - allOf(...) waits for many; anyOf(...) for the first
//  - exceptionally(fn) recovers with a fallback value; handle(fn) sees
//    either the result or the exception
//  - orTimeout / completeOnTimeout bound how long a stage may take
// The calls below simulate a login: look up the user, then (in parallel)
// their roles and MFA status, then decide.
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CompletableFutureCompositionExample {
    record User(int id, String name) {}

    static final Map<String, User> USERS = Map.of("alice", new User(1, "alice"), "bob", new User(2, "bob"));
    static ExecutorService io;

    static CompletableFuture<User> findUser(String name) {
        return CompletableFuture.supplyAsync(() -> {
            User u = USERS.get(name);
            if (u == null) throw new IllegalArgumentException("no such user: " + name);
            return u;
        }, io);
    }

    static CompletableFuture<List<String>> roles(User u) {
        return CompletableFuture.supplyAsync(() -> u.id() == 1 ? List.of("admin", "dev") : List.of("dev"), io);
    }

    static CompletableFuture<Boolean> mfaEnrolled(User u) {
        return CompletableFuture.supplyAsync(() -> u.id() == 1, io);
    }

    static CompletableFuture<String> login(String name) {
        return findUser(name)
                // dependent call: needs the User first
                .thenCompose(u -> roles(u)
                        // independent calls: run both, combine when both are done
                        .thenCombine(mfaEnrolled(u), (rs, mfa) ->
                                rs.contains("admin") && !mfa ? name + ": denied (admin without MFA)"
                                        : name + ": granted " + rs + (mfa ? " with MFA" : "")))
                // a failure anywhere above lands here
                .exceptionally(ex -> name + ": error " + ex.getCause().getMessage());
    }

    public static void main(String[] args) {
        io = Executors.newFixedThreadPool(4);
        try {
            for (String name : List.of("alice", "bob", "mallory")) {
                System.out.println(login(name).join());
            }

            // allOf: wait for a batch, then read each (already completed) result
            List<CompletableFuture<String>> batch = List.of(login("bob"), login("alice"), login("eve"));
            CompletableFuture.allOf(batch.toArray(CompletableFuture[]::new)).join();
            System.out.println("batch: " + batch.stream().map(CompletableFuture::join).toList());

            // handle: one callback for both outcomes
            String handled = findUser("trudy")
                    .handle((u, ex) -> ex == null ? "found " + u.name() : "handled " + ex.getCause().getClass().getSimpleName())
                    .join();
            System.out.println(handled);

            // Timeouts: a stage that never completes on its own
            CompletableFuture<String> hung = new CompletableFuture<>();
            String fallback = hung.copy().completeOnTimeout("cached result", 50, TimeUnit.MILLISECONDS).join();
            System.out.println("completeOnTimeout -> " + fallback);
            String timedOut = hung.orTimeout(50, TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> "orTimeout -> " + ex.getClass().getSimpleName())
                    .join();
            System.out.println(timedOut + " (is TimeoutException: "
                    + (hung.exceptionNow() instanceof TimeoutException) + ")");
        } finally {
            io.shutdown();
        }
    }
}

/* Output:
alice: granted [admin, dev] with MFA
bob: granted [dev]
mallory: error no such user: mallory
batch: [bob: granted [dev], alice: granted [admin, dev] with MFA, eve: error no such user: eve]
handled IllegalArgumentException
completeOnTimeout -> cached result
orTimeout -> TimeoutException (is TimeoutException: true)
*/
