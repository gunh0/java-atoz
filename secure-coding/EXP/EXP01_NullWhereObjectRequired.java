// EXP01-J. Do not use a null in a case where an object is required
// https://wiki.sei.cmu.edu/confluence/display/java/EXP01-J.+Do+not+use+a+null+in+a+case+where+an+object+is+required
//
// Dereferencing null throws NullPointerException, and null hides in places
// that do not look like dereferences: unboxing a missing Map value into an
// int or boolean, switching on a null String or enum, or calling equals()
// on a field that was never set. In a security check the result is either a
// crash (denial of service) or, if the exception is swallowed somewhere,
// a decision made on missing data. Decide explicitly what null means:
// Map.getOrDefault() with a safe default, Objects.equals(), a constant on
// the left of equals(), or (since Java 21) a switch with a case null.
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class EXP01_NullWhereObjectRequired {
    static final Map<String, Boolean> IS_ADMIN = new HashMap<>(Map.of("alice", true, "bob", false));
    static final Map<String, String> ROLE = new HashMap<>(Map.of("alice", "admin", "bob", "user"));

    public static void main(String[] args) {
        for (String user : new String[] {"alice", "mallory"}) {
            System.out.println(user + ":");
            System.out.println("  noncompliant isAdmin: " + attempt(() -> isAdminNoncompliant(user)));
            System.out.println("  compliant isAdmin:    " + isAdminCompliant(user));
            System.out.println("  noncompliant quota:   " + attempt(() -> quotaNoncompliant(ROLE.get(user))));
            System.out.println("  compliant quota:      " + quotaCompliant(ROLE.get(user)));
        }
    }

    // Noncompliant: auto-unboxing a missing value throws NullPointerException
    static boolean isAdminNoncompliant(String user) {
        return IS_ADMIN.get(user);
    }

    // Compliant: missing means "not an admin"
    static boolean isAdminCompliant(String user) {
        return IS_ADMIN.getOrDefault(user, false);
    }

    // Noncompliant: switch on a null String throws NullPointerException
    static int quotaNoncompliant(String role) {
        return switch (role) {
            case "admin" -> 1_000;
            case "user" -> 100;
            default -> 0;
        };
    }

    // Compliant: null is a case of its own
    static int quotaCompliant(String role) {
        return switch (role) {
            case "admin" -> 1_000;
            case "user" -> 100;
            case null, default -> 0;
        };
    }

    static String attempt(Supplier<Object> action) {
        try {
            return String.valueOf(action.get());
        } catch (NullPointerException e) {
            return "NullPointerException";
        }
    }
}

/* Output:
alice:
  noncompliant isAdmin: true
  compliant isAdmin:    true
  noncompliant quota:   1000
  compliant quota:      1000
mallory:
  noncompliant isAdmin: NullPointerException
  compliant isAdmin:    false
  noncompliant quota:   NullPointerException
  compliant quota:      0
*/
