// SEC05-J. Do not use reflection to increase accessibility of classes, methods, or fields
// https://wiki.sei.cmu.edu/confluence/display/java/SEC05-J.+Do+not+use+reflection+to+increase+accessibility+of+classes%2C+methods%2C+or+fields
//
// setAccessible(true) switches off Java's access checks for one member. A
// generic helper that looks up a field or method by a caller-supplied name
// and makes it accessible turns every private member of every class it can
// reach into public API: a "get property by name" endpoint becomes a way
// to read password hashes or flip internal flags. (Strong encapsulation of
// the JDK's own modules blocks this for java.* internals, but not for the
// application's own classes on the class path.) Do not widen access with
// reflection on behalf of untrusted callers; expose the intended members
// explicitly, or reflect only over public members (getMethod/getField).
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public class SEC05_ReflectionAccessibility {
    public static void main(String[] args) {
        User user = new User("alice", "$2b$12$Qx9...hash", true);
        for (String property : new String[] {"name", "passwordHash"}) {
            System.out.println("property " + property);
            System.out.println("  noncompliant: " + getNoncompliant(user, property));
            System.out.println("  compliant:    " + getCompliant(user, property));
        }
        System.out.println("noncompliant set active=false: " + setNoncompliant(user, "active", false) + ", active now " + user.isActive());
    }

    // Noncompliant: any field, private or not, by name
    static String getNoncompliant(Object target, String name) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return String.valueOf(f.get(target));
        } catch (ReflectiveOperationException e) {
            return "no such property";
        }
    }

    static String setNoncompliant(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
            return "done";
        } catch (ReflectiveOperationException e) {
            return "failed";
        }
    }

    // Compliant: only public getter methods, never setAccessible
    static String getCompliant(Object target, String name) {
        try {
            Method m = target.getClass().getMethod("get" + name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1));
            return String.valueOf(m.invoke(target));
        } catch (ReflectiveOperationException e) {
            return "no such property (" + e.getClass().getSimpleName() + ")";
        }
    }
}

class User {
    private final String name;
    private final String passwordHash;
    private boolean active;

    User(String name, String passwordHash, boolean active) {
        this.name = name;
        this.passwordHash = passwordHash;
        this.active = active;
    }

    public String getName() {
        return name;
    }

    boolean checkPassword(String hash) {
        return passwordHash.equals(hash);
    }

    boolean isActive() {
        return active;
    }
}

/* Output:
property name
  noncompliant: alice
  compliant:    alice
property passwordHash
  noncompliant: $2b$12$Qx9...hash
  compliant:    no such property (NoSuchMethodException)
noncompliant set active=false: done, active now false
*/
