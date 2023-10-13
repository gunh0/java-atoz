// Effective Java, Item 73: Throw exceptions appropriate to the abstraction.
//
// When a low-level exception escapes from a high-level method, it leaks
// implementation details into the API and ties callers to that
// implementation: switch the storage from a file to a map and the
// exceptions callers see change too. Higher layers should catch low-level
// exceptions and throw ones that make sense in their own terms (exception
// translation), passing the original as the cause (exception chaining) so
// nothing is lost for debugging. Better still, avoid the low-level failure
// altogether by validating parameters before passing them down.
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

public class ExceptionTranslation {
    // High-level exception that speaks the language of the abstraction
    static class UserNotFoundException extends Exception {
        UserNotFoundException(String id, Throwable cause) {
            super("no user with id " + id, cause);
        }
    }

    static final class UserDirectory {
        private final Map<String, String> byId = new HashMap<>(Map.of("u1", "ann", "u2", "bob"));
        private final String[] slots = {"ann", "bob"};

        // Leaky: callers see an array index exception from a "directory"
        String nameAtLeaky(int slot) {
            return slots[slot];
        }

        // Translated and chained
        String nameAt(int slot) throws UserNotFoundException {
            try {
                return slots[slot];
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new UserNotFoundException("slot " + slot, e);
            }
        }

        // Even better: check first so the low-level failure cannot happen
        String find(String id) throws UserNotFoundException {
            String name = byId.get(id);
            if (name == null) {
                throw new UserNotFoundException(id, null);
            }
            return name;
        }
    }

    public static void main(String[] args) {
        UserDirectory dir = new UserDirectory();
        try {
            dir.nameAtLeaky(5);
        } catch (RuntimeException e) {
            System.out.println("leaky:      " + e.getClass().getName());
        }
        try {
            dir.nameAt(5);
        } catch (UserNotFoundException e) {
            System.out.println("translated: " + e.getMessage());
            System.out.println("  cause:    " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
        }
        try {
            dir.find("u9");
        } catch (UserNotFoundException e) {
            System.out.println("checked up front: " + e.getMessage() + ", cause = " + e.getCause());
        }

        // The JDK follows the same rule: a list iterator walked past the end
        // reports NoSuchElementException (iterator vocabulary), not the
        // IndexOutOfBoundsException that the list's get() would throw
        Iterator<String> it = java.util.List.of("only").iterator();
        it.next();
        try {
            it.next();
        } catch (NoSuchElementException e) {
            System.out.println("JDK iterator past the end: " + e.getClass().getSimpleName());
        }
    }
}

/* Output:
leaky:      java.lang.ArrayIndexOutOfBoundsException
translated: no user with id slot 5
  cause:    java.lang.ArrayIndexOutOfBoundsException: Index 5 out of bounds for length 2
checked up front: no user with id u9, cause = null
JDK iterator past the end: NoSuchElementException
*/
