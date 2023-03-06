// ERR00-J. Do not suppress or ignore checked exceptions
// https://wiki.sei.cmu.edu/confluence/display/java/ERR00-J.+Do+not+suppress+or+ignore+checked+exceptions
//
// An empty catch block, or one that only prints a stack trace, lets the
// program carry on as if the operation had succeeded. When that operation
// was loading an access policy, the code after it runs with whatever
// default happened to be in place, often the permissive one. Handle the
// exception (recover to a safe state, fail closed) or let it propagate.
// InterruptedException is a special case: if it cannot be rethrown, restore
// the interrupt status with Thread.currentThread().interrupt().
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ERR00_SuppressedExceptions {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("err00");
        Path policy = dir.resolve("policy.properties"); // was never written: loading fails
        try {
            System.out.println("noncompliant: guests allowed = " + new PolicyNoncompliant().load(policy).guestsAllowed());
            try {
                new PolicyCompliant().load(policy);
            } catch (UncheckedIOException e) {
                System.out.println("compliant:    " + e.getMessage() + " -> requests denied");
            }
        } finally {
            Files.delete(dir);
        }

        // Restoring the interrupt status keeps the cancellation request visible
        Thread.currentThread().interrupt();
        sleepNoncompliant();
        System.out.println("interrupt status after noncompliant sleep: " + Thread.interrupted());
        Thread.currentThread().interrupt();
        sleepCompliant();
        System.out.println("interrupt status after compliant sleep:    " + Thread.interrupted());
    }

    static void sleepNoncompliant() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
            // Noncompliant: the request to stop is lost
        }
    }

    static void sleepCompliant() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // let callers see it
        }
    }
}

class PolicyNoncompliant {
    private boolean allowGuests = true; // the "until loaded" default

    PolicyNoncompliant load(Path file) {
        try (Reader in = Files.newBufferedReader(file)) {
            Properties p = new Properties();
            p.load(in);
            allowGuests = Boolean.parseBoolean(p.getProperty("allowGuests", "false"));
        } catch (IOException e) {
            // Noncompliant: ignored; the permissive default stays in effect
        }
        return this;
    }

    boolean guestsAllowed() {
        return allowGuests;
    }
}

class PolicyCompliant {
    private boolean allowGuests = false; // fail closed

    PolicyCompliant load(Path file) {
        try (Reader in = Files.newBufferedReader(file)) {
            Properties p = new Properties();
            p.load(in);
            allowGuests = Boolean.parseBoolean(p.getProperty("allowGuests", "false"));
            return this;
        } catch (IOException e) {
            // Compliant: report the failure instead of continuing silently
            throw new UncheckedIOException("policy could not be loaded: " + e.getClass().getSimpleName(), e);
        }
    }

    boolean guestsAllowed() {
        return allowGuests;
    }
}

/* Output:
noncompliant: guests allowed = true
compliant:    policy could not be loaded: NoSuchFileException -> requests denied
interrupt status after noncompliant sleep: false
interrupt status after compliant sleep:    true
*/
