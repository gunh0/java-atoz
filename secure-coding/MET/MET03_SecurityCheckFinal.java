// MET03-J. Methods that perform a security check must be declared private or final
// https://wiki.sei.cmu.edu/confluence/display/java/MET03-J.+Methods+that+perform+a+security+check+must+be+declared+private+or+final
//
// A security check placed in an overridable method is only as strong as
// the most permissive subclass. Anyone who can extend the class (a plugin,
// a library user, a deserialized gadget) can override the check with
// `return true` or a no-op and still pass the object to code that trusts
// the base type. Declare methods that perform access checks private or
// final (or the whole class final or sealed), so the check that runs is
// always the one you wrote.
import java.util.Set;

public class MET03_SecurityCheckFinal {
    public static void main(String[] args) {
        User guest = new User("guest", Set.of("read"));

        DocumentStoreNoncompliant base = new DocumentStoreNoncompliant();
        DocumentStoreNoncompliant plugin = new SneakyStore();
        System.out.println("noncompliant base store, guest deletes:    " + attempt(() -> base.delete(guest, "payroll.xlsx")));
        System.out.println("noncompliant plugin store, guest deletes:  " + attempt(() -> plugin.delete(guest, "payroll.xlsx")));

        DocumentStoreCompliant safe = new LoggingStore();
        System.out.println("compliant subclass store, guest deletes:   " + attempt(() -> safe.delete(guest, "payroll.xlsx")));
    }

    static String attempt(Runnable action) {
        try {
            action.run();
            return "allowed";
        } catch (SecurityException e) {
            return "denied (" + e.getMessage() + ")";
        }
    }
}

record User(String name, Set<String> permissions) {}

class DocumentStoreNoncompliant {
    // Noncompliant: overridable check
    protected void checkPermission(User user, String permission) {
        if (!user.permissions().contains(permission)) {
            throw new SecurityException(user.name() + " lacks " + permission);
        }
    }

    public void delete(User user, String doc) {
        checkPermission(user, "delete");
        // ... delete the document
    }
}

// Extends the store "to add caching" and quietly disables the check
class SneakyStore extends DocumentStoreNoncompliant {
    @Override
    protected void checkPermission(User user, String permission) {
        // allow everything
    }
}

class DocumentStoreCompliant {
    // Compliant: subclasses can reuse it but cannot replace it
    protected final void checkPermission(User user, String permission) {
        if (!user.permissions().contains(permission)) {
            throw new SecurityException(user.name() + " lacks " + permission);
        }
    }

    public void delete(User user, String doc) {
        checkPermission(user, "delete");
        onDelete(doc);
    }

    // Extension point that runs only after the check
    protected void onDelete(String doc) {
    }
}

class LoggingStore extends DocumentStoreCompliant {
    // Overriding checkPermission here would not compile:
    // "checkPermission(User,String) in LoggingStore cannot override ... overridden method is final"
    @Override
    protected void onDelete(String doc) {
        System.out.println("deleted " + doc);
    }
}

/* Output:
noncompliant base store, guest deletes:    denied (guest lacks delete)
noncompliant plugin store, guest deletes:  allowed
compliant subclass store, guest deletes:   denied (guest lacks delete)
*/
