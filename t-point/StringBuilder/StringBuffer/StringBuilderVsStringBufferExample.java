// StringBuffer (Java 1.0) and StringBuilder (Java 5) have the same API. The
// difference is that every StringBuffer method is synchronized, so a single
// instance can be shared between threads; StringBuilder has no locking and is
// the right choice for the usual case of a buffer owned by one method.
//
//                  String           StringBuilder   StringBuffer
//   mutable        no               yes             yes
//   thread-safe    yes (immutable)  no              yes (synchronized)
//
// "Thread-safe" only covers single calls: a check-then-append sequence on a
// StringBuffer still needs external synchronization.
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class StringBuilderVsStringBufferExample {
    private static final int THREADS = 4;
    private static final int APPENDS_PER_THREAD = 10_000;

    public static void main(String[] args) throws Exception {
        // Same API, same result in single-threaded code
        StringBuilder builder = new StringBuilder();
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < 5; i++) {
            builder.append(i).append(';');
            buffer.append(i).append(';');
        }
        System.out.println("builder: " + builder);
        System.out.println("buffer:  " + buffer);

        // Shared between threads: StringBuffer never loses an append
        StringBuffer shared = new StringBuffer();
        runConcurrently(() -> shared.append('x'));
        System.out.println("StringBuffer length after concurrent appends: " + shared.length()
                + " (expected " + THREADS * APPENDS_PER_THREAD + ")");

        // The difference is visible in the method modifiers. A StringBuilder
        // shared the same way can lose appends or throw while growing its
        // buffer, and the damage varies from run to run.
        for (Class<?> type : new Class<?>[] {StringBuilder.class, StringBuffer.class}) {
            Method append = type.getMethod("append", String.class);
            System.out.println(type.getSimpleName() + ".append(String) synchronized: "
                    + Modifier.isSynchronized(append.getModifiers()));
        }

        // Single calls are atomic, compound actions are not
        StringBuffer log = new StringBuffer();
        synchronized (log) { // the lock StringBuffer itself uses
            if (log.length() == 0) {
                log.append("header\n");
            }
        }
        System.out.print(log);
    }

    private static void runConcurrently(Runnable action) throws InterruptedException {
        Thread[] threads = new Thread[THREADS];
        for (int t = 0; t < THREADS; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < APPENDS_PER_THREAD; i++) action.run();
            });
            threads[t].start();
        }
        for (Thread t : threads) t.join();
    }
}

/* Output:
builder: 0;1;2;3;4;
buffer:  0;1;2;3;4;
StringBuffer length after concurrent appends: 40000 (expected 40000)
StringBuilder.append(String) synchronized: false
StringBuffer.append(String) synchronized: true
header
*/
