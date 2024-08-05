// Exception chaining keeps the original failure when a layer translates a
// low-level exception into one that fits its own abstraction:
//  - pass the cause to the constructor: new ServiceException("...", e)
//  - or call initCause(e) once, for exception types without such a constructor
//  - getCause() walks one step down; loop to find the root cause
// Without the cause, the log shows "lookup failed" and nothing about why.
// Translate at layer boundaries (repository -> service -> API) so callers
// don't depend on SQL, I/O or parsing details, but never drop the cause.
import java.io.IOException;
import java.io.UncheckedIOException;

public class ChainingExample {

    static class RepositoryException extends Exception {
        RepositoryException(String message, Throwable cause) { super(message, cause); }
    }

    static class ServiceException extends RuntimeException {
        ServiceException(String message, Throwable cause) { super(message, cause); }
    }

    // A legacy exception type with no (message, cause) constructor
    static class LegacyException extends Exception {
        LegacyException(String message) { super(message); }
    }

    // Lowest layer: parsing a stored record
    static int parseBalance(String raw) {
        return Integer.parseInt(raw); // NumberFormatException for bad data
    }

    // Repository layer translates the parse failure
    static int loadBalance(String id, String raw) throws RepositoryException {
        try {
            return parseBalance(raw);
        } catch (NumberFormatException e) {
            throw new RepositoryException("corrupt balance for " + id, e);
        }
    }

    // Service layer translates to its own unchecked type
    static int balance(String id, String raw) {
        try {
            return loadBalance(id, raw);
        } catch (RepositoryException e) {
            throw new ServiceException("balance lookup failed for " + id, e);
        }
    }

    static Throwable rootCause(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    static void printChain(Throwable t) {
        int depth = 0;
        for (Throwable c = t; c != null; c = c.getCause()) {
            // Own messages only; the JDK's own message text is not stable output
            String msg = c.getClass().getName().startsWith("java.") ? "" : ": " + c.getMessage();
            System.out.println("  ".repeat(depth + 1) + (depth == 0 ? "" : "caused by ") + c.getClass().getSimpleName() + msg);
            depth++;
        }
    }

    public static void main(String[] args) {
        System.out.println("balance(alice) = " + balance("alice", "120"));

        try {
            balance("bob", "12O"); // letter O instead of zero
        } catch (ServiceException e) {
            System.out.println("chain:");
            printChain(e);
            System.out.println("root cause: " + rootCause(e).getClass().getSimpleName());
        }

        // initCause: attach a cause after construction (allowed only once)
        LegacyException legacy = new LegacyException("legacy call failed");
        legacy.initCause(new IOException("disk read error"));
        System.out.println("legacy cause: " + legacy.getCause().getClass().getSimpleName());
        try {
            legacy.initCause(new RuntimeException());
        } catch (IllegalStateException e) {
            System.out.println("second initCause -> " + e.getClass().getSimpleName());
        }

        // Wrapping checked I/O exceptions in lambdas: UncheckedIOException requires a cause
        try {
            throw new UncheckedIOException("reading config", new IOException("device not ready"));
        } catch (UncheckedIOException e) {
            System.out.println("unwrapped: " + e.getCause().getClass().getSimpleName() + " (" + e.getCause().getMessage() + ")");
        }

        // The anti-pattern: translating without the cause loses the root
        ServiceException lossy = new ServiceException("balance lookup failed", null);
        System.out.println("lossy cause: " + lossy.getCause());
    }
}

/* Output:
balance(alice) = 120
chain:
  ServiceException: balance lookup failed for bob
    caused by RepositoryException: corrupt balance for bob
      caused by NumberFormatException
root cause: NumberFormatException
legacy cause: IOException
second initCause -> IllegalStateException
unwrapped: IOException (device not ready)
lossy cause: null
*/
