// Encapsulation: keep an object's state private and expose only the
// operations that keep it valid. Callers cannot put the object into an
// impossible state, and the representation can change without breaking them.
//
// Access modifiers, from most to least open:
//   public      everywhere
//   protected   same package + subclasses (in any package)
//   (default)   same package only ("package-private", no keyword)
//   private     the declaring class only (and its nested classes)
// Mutable values handed in or out (arrays, lists, Date) should be copied, or
// callers can change the internal state behind the object's back.
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EncapsulationExample {
    public static void main(String[] args) {
        UserAccount user = new UserAccount("kim");
        user.setEmail("kim@example.com");
        System.out.println(user);

        // Validation in the setter rejects bad state
        try {
            user.setEmail("not-an-email");
        } catch (IllegalArgumentException e) {
            System.out.println("rejected: " + e.getMessage());
        }

        // Defensive copy on the way in
        List<String> roles = new ArrayList<>(List.of("viewer"));
        user.setRoles(roles);
        roles.add("admin");                       // caller's list, not ours
        System.out.println("roles after caller edits its list: " + user.getRoles());

        // Read-only view on the way out
        try {
            user.getRoles().add("admin");
        } catch (UnsupportedOperationException e) {
            System.out.println("getRoles() is read-only");
        }

        // Behaviour instead of raw field access
        for (int i = 0; i < 4; i++) {
            user.recordFailedLogin();
        }
        System.out.println(user);
        // user.failedLogins = 0;                 // compile error: private
    }
}

class UserAccount {
    private static final int MAX_FAILED_LOGINS = 3;

    private final String username;
    private String email;
    private List<String> roles = List.of();
    private int failedLogins;
    private boolean locked;

    UserAccount(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;                          // no setter: effectively read-only
    }

    public void setEmail(String email) {
        if (email == null || !email.matches("[^@\\s]+@[^@\\s]+\\.[a-z]+")) {
            throw new IllegalArgumentException("invalid email: " + email);
        }
        this.email = email;
    }

    public void setRoles(List<String> roles) {
        this.roles = new ArrayList<>(roles);      // copy in
    }

    public List<String> getRoles() {
        return Collections.unmodifiableList(roles); // read-only view out
    }

    public void recordFailedLogin() {
        failedLogins++;
        if (failedLogins >= MAX_FAILED_LOGINS) {
            locked = true;                        // invariant kept in one place
        }
    }

    @Override
    public String toString() {
        return username + " <" + email + "> failed=" + failedLogins + " locked=" + locked;
    }
}

/* Output:
kim <kim@example.com> failed=0 locked=false
rejected: invalid email: not-an-email
roles after caller edits its list: [viewer]
getRoles() is read-only
kim <kim@example.com> failed=4 locked=true
*/
