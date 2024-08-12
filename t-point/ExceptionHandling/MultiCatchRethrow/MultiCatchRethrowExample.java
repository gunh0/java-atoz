// Multi-catch and precise rethrow (both Java 7) remove duplicated handlers:
//  - catch (A | B e) handles unrelated types in one block; e is implicitly
//    final and its static type is their closest common supertype
//  - alternatives may not be subclasses of each other (javac rejects it)
//  - precise rethrow: catching a broad type (Exception e) and rethrowing an
//    effectively final e still lets the method declare only the checked
//    exceptions the try block can actually throw
// For cleanup that can fail on its own, addSuppressed() keeps the secondary
// failure attached to the primary one instead of replacing it.
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

public class MultiCatchRethrowExample {

    static void call(String what) throws IOException, TimeoutException {
        switch (what) {
            case "io" -> throw new IOException("connection reset");
            case "timeout" -> throw new TimeoutException("no answer in 2s");
            case "bad-arg" -> throw new IllegalArgumentException("port must be > 0");
            default -> System.out.println("  " + what + " ok");
        }
    }

    // One handler for two retryable failures
    static void withMultiCatch(String what) {
        try {
            call(what);
        } catch (IOException | TimeoutException e) {
            // e = new IOException(); would not compile: e is final
            System.out.println("  retryable " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("  rejected: " + e.getMessage());
        }
    }

    // Precise rethrow: the clause catches Exception, yet the method only declares
    // the two checked types call() can throw, not "throws Exception".
    static void logAndRethrow(String what) throws IOException, TimeoutException {
        try {
            call(what);
        } catch (Exception e) {
            System.out.println("  log: " + e.getClass().getSimpleName());
            throw e;
        }
    }

    // Manual suppression: cleanup failure must not hide the original error
    static void processThenCleanup(boolean workFails, boolean cleanupFails) throws IOException {
        IOException primary = null;
        try {
            if (workFails) throw new IOException("work failed");
            System.out.println("  work ok");
        } catch (IOException e) {
            primary = e;
            throw e;
        } finally {
            try {
                if (cleanupFails) throw new IOException("cleanup failed");
            } catch (IOException cleanupError) {
                if (primary != null) {
                    primary.addSuppressed(cleanupError);
                } else {
                    throw cleanupError;
                }
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("multi-catch:");
        for (String what : new String[] {"ping", "io", "timeout", "bad-arg"}) {
            withMultiCatch(what);
        }

        System.out.println("precise rethrow:");
        for (String what : new String[] {"timeout", "bad-arg"}) {
            try {
                logAndRethrow(what);
            } catch (IOException | TimeoutException e) {
                System.out.println("  caller got checked " + e.getClass().getSimpleName());
            } catch (RuntimeException e) {
                System.out.println("  caller got unchecked " + e.getClass().getSimpleName());
            }
        }

        System.out.println("suppressed:");
        boolean[][] cases = {{false, false}, {true, false}, {false, true}, {true, true}};
        for (boolean[] c : cases) {
            System.out.println("work fails=" + c[0] + ", cleanup fails=" + c[1] + ":");
            try {
                processThenCleanup(c[0], c[1]);
            } catch (IOException e) {
                String suppressed = Arrays.stream(e.getSuppressed()).map(Throwable::getMessage).toList().toString();
                System.out.println("  thrown: " + e.getMessage() + ", suppressed: " + suppressed);
            }
        }
    }
}

/* Output:
multi-catch:
  ping ok
  retryable IOException: connection reset
  retryable TimeoutException: no answer in 2s
  rejected: port must be > 0
precise rethrow:
  log: TimeoutException
  caller got checked TimeoutException
  log: IllegalArgumentException
  caller got unchecked IllegalArgumentException
suppressed:
work fails=false, cleanup fails=false:
  work ok
work fails=true, cleanup fails=false:
  thrown: work failed, suppressed: []
work fails=false, cleanup fails=true:
  work ok
  thrown: cleanup failed, suppressed: []
work fails=true, cleanup fails=true:
  thrown: work failed, suppressed: [cleanup failed]
*/
