// ERR08-J. Do not catch NullPointerException or any of its ancestors
// https://wiki.sei.cmu.edu/confluence/display/java/ERR08-J.+Do+not+catch+NullPointerException+or+any+of+its+ancestors
//
// Catching NullPointerException as a substitute for a null check is slower,
// but the real problem is that it catches EVERY null dereference in the try
// block, including genuine bugs in the code being called. The failure is
// turned into a normal-looking result and the program carries on in an
// inconsistent state; in a validator it can even flip a security decision.
// The same goes for catching RuntimeException, Exception or Throwable.
// Test for null explicitly where null is a legal value, and let unexpected
// NullPointerExceptions propagate so the bug is found.
import java.util.Map;
import java.util.Set;

public class ERR08_CatchNullPointerException {
    public static void main(String[] args) {
        Validator healthy = new Validator(Set.of("alice", "bob"));
        Validator broken = new Validator(null); // a bug: the deny list was never loaded

        for (String user : new String[] {"alice", "mallory", null}) {
            System.out.printf("user %-8s healthy: noncompliant=%-5s compliant=%-5s | broken: noncompliant=%-5s compliant=%s%n",
                    user, healthy.isAllowedNoncompliant(user), healthy.isAllowedCompliant(user),
                    broken.isAllowedNoncompliant(user), tryCompliant(broken, user));
        }
    }

    static String tryCompliant(Validator v, String user) {
        try {
            return String.valueOf(v.isAllowedCompliant(user));
        } catch (NullPointerException e) {
            return "NPE surfaced (bug found)";
        }
    }

    static final class Validator {
        private final Set<String> known;
        private final Map<String, Boolean> denied = Map.of("mallory", true);

        Validator(Set<String> known) {
            this.known = known;
        }

        // Noncompliant: the catch block is meant for a null user name, but it
        // also hides the null 'known' set, and turns "deny" into "allow"
        boolean isAllowedNoncompliant(String user) {
            try {
                if (user.isEmpty() || denied.containsKey(user)) {
                    return false;
                }
                return known.contains(user) || !user.startsWith("tmp_");
            } catch (NullPointerException e) {
                return !denied.containsKey(String.valueOf(user));
            }
        }

        // Compliant: null input is handled explicitly; other nulls are bugs
        boolean isAllowedCompliant(String user) {
            if (user == null || user.isEmpty() || denied.containsKey(user)) {
                return false;
            }
            return known.contains(user) || !user.startsWith("tmp_");
        }
    }
}

/* Output:
user alice    healthy: noncompliant=true  compliant=true  | broken: noncompliant=true  compliant=NPE surfaced (bug found)
user mallory  healthy: noncompliant=false compliant=false | broken: noncompliant=false compliant=false
user null     healthy: noncompliant=true  compliant=false | broken: noncompliant=true  compliant=false
*/
