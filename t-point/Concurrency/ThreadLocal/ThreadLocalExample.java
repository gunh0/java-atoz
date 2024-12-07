// ThreadLocal<T> gives every thread its own independent copy of a value.
// Typical uses: per-request context (current user, trace id) that would
// otherwise be passed through every method, and per-thread instances of
// non-thread-safe helpers such as SimpleDateFormat or a MessageDigest.
//  - ThreadLocal.withInitial(supplier) creates the value lazily per thread
//  - in thread pools threads are reused, so ALWAYS remove() in a finally
//    block, or the next task on that thread sees stale data (and the value
//    may leak memory)
//  - InheritableThreadLocal copies the parent's value into threads it creates
// For new code with virtual threads, prefer passing context explicitly.
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ThreadLocalExample {
    // Per-request context
    static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();

    // SimpleDateFormat is not thread-safe: one instance per thread
    static final ThreadLocal<SimpleDateFormat> UTC_FORMAT = ThreadLocal.withInitial(() -> {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f;
    });

    static final InheritableThreadLocal<String> TRACE_ID = new InheritableThreadLocal<>();

    static String audit(String action) {
        // Deep in the call stack, no user parameter needed
        String user = CURRENT_USER.get();
        return (user == null ? "<anonymous>" : user) + " " + action;
    }

    public static void main(String[] args) throws Exception {
        // 1. Each thread sees only its own value
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();
        for (String user : List.of("alice", "bob", "carol")) {
            threads.add(Thread.ofPlatform().start(() -> {
                CURRENT_USER.set(user);
                seen.add(audit("logged in"));
                CURRENT_USER.remove();
            }));
        }
        for (Thread t : threads) t.join();
        seen.stream().sorted().forEach(s -> System.out.println("  " + s));
        System.out.println("main thread: " + audit("checked status"));

        // 2. withInitial: same instance on repeated calls in one thread, different across threads
        SimpleDateFormat mine = UTC_FORMAT.get();
        SimpleDateFormat[] other = new SimpleDateFormat[1];
        Thread t = Thread.ofPlatform().start(() -> other[0] = UTC_FORMAT.get());
        t.join();
        System.out.println("same instance in one thread: " + (mine == UTC_FORMAT.get()));
        System.out.println("different instance per thread: " + (mine != other[0]));
        System.out.println("formatted: " + mine.format(new Date(1_700_000_000_000L)));

        // 3. Thread pools reuse threads: forgetting remove() leaks state into the next task
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> CURRENT_USER.set("admin")).get();              // forgot remove()
            Future<String> next = pool.submit(() -> audit("deleted a record"));
            System.out.println("without remove(): " + next.get());

            pool.submit(() -> {
                CURRENT_USER.set("admin");
                try {
                    return audit("rotated keys");
                } finally {
                    CURRENT_USER.remove();                                     // always clean up
                }
            }).get();
            System.out.println("with remove():    " + pool.submit(() -> audit("deleted a record")).get());
        } finally {
            pool.shutdown();
        }

        // 4. InheritableThreadLocal: child threads start with the parent's value
        TRACE_ID.set("trace-42");
        CURRENT_USER.set("alice");
        String[] fromChild = new String[2];
        Thread child = Thread.ofPlatform().start(() -> {
            fromChild[0] = TRACE_ID.get();
            fromChild[1] = String.valueOf(CURRENT_USER.get());
        });
        child.join();
        System.out.println("child sees trace id: " + fromChild[0] + ", plain ThreadLocal: " + fromChild[1]);
        TRACE_ID.remove();
        CURRENT_USER.remove();
    }
}

/* Output:
  alice logged in
  bob logged in
  carol logged in
main thread: <anonymous> checked status
same instance in one thread: true
different instance per thread: true
formatted: 2023-11-14 22:13:20 UTC
without remove(): admin deleted a record
with remove():    <anonymous> deleted a record
child sees trace id: trace-42, plain ThreadLocal: null
*/
