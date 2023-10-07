// ERR06-J. Do not throw undeclared checked exceptions
// https://wiki.sei.cmu.edu/confluence/display/java/ERR06-J.+Do+not+throw+undeclared+checked+exceptions
//
// The compiler's checked-exception rules only exist at compile time. With a
// generic "sneaky throw" (a cast to a type variable that is erased to
// Throwable), Class.newInstance(), or bytecode from other languages, a
// method can throw a checked exception it does not declare. Callers that
// wrote catch blocks based on the signature (catch RuntimeException, say)
// never see it coming, so their cleanup or fail-safe path is skipped and the
// exception escapes into code that cannot handle it. Declare checked
// exceptions, or wrap them in an unchecked exception that callers can
// expect (UncheckedIOException, or a domain exception).
import java.io.IOException;
import java.io.UncheckedIOException;

public class ERR06_UndeclaredCheckedExceptions {
    public static void main(String[] args) {
        System.out.println("noncompliant: " + handle(ERR06_UndeclaredCheckedExceptions::loadNoncompliant));
        System.out.println("compliant:    " + handle(ERR06_UndeclaredCheckedExceptions::loadCompliant));
    }

    // A request handler that relies on the loader's signature: no checked exceptions
    static String handle(Runnable loader) {
        try {
            try {
                loader.run();
                return "loaded";
            } catch (RuntimeException e) {
                return "handled " + e.getClass().getSimpleName() + ", fell back to safe defaults";
            }
        } catch (Throwable t) {
            // Only reached because something broke the rules
            return "escaped the handler: " + t.getClass().getName();
        }
    }

    // Noncompliant: throws IOException without declaring it
    static void loadNoncompliant() {
        Sneaky.<RuntimeException>sneakyThrow(new IOException("disk read failed"));
    }

    // Compliant: wraps the checked exception in an unchecked one
    static void loadCompliant() {
        try {
            throw new IOException("disk read failed");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

class Sneaky {
    @SuppressWarnings("unchecked")
    static <T extends Throwable> void sneakyThrow(Throwable t) throws T {
        throw (T) t; // unchecked cast, erased: the IOException is thrown as is
    }
}

/* Output:
noncompliant: escaped the handler: java.io.IOException
compliant:    handled UncheckedIOException, fell back to safe defaults
*/
