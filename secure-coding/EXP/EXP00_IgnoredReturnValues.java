// EXP00-J. Do not ignore values returned by methods
// https://wiki.sei.cmu.edu/confluence/display/java/EXP00-J.+Do+not+ignore+values+returned+by+methods
//
// Many methods report their result only through the return value: methods
// of immutable types (String.replace, BigDecimal.add) return a NEW object
// and leave the receiver unchanged, and methods like Set.add, File.delete
// or Lock.tryLock return a boolean that says whether anything happened.
// Ignoring that value means the program continues as if the operation had
// succeeded: the "sanitized" string still contains the attack, the
// duplicate account is never noticed. Use the returned object, or check
// the status and handle the failure.
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

public class EXP00_IgnoredReturnValues {
    public static void main(String[] args) {
        String upload = "../../etc/cron.d/job";
        System.out.println("sanitize  noncompliant: " + sanitizeNoncompliant(upload));
        System.out.println("sanitize  compliant:    " + sanitizeCompliant(upload));

        BigDecimal balance = new BigDecimal("100.00");
        System.out.println("deposit   noncompliant: " + depositNoncompliant(balance, new BigDecimal("25.50")));
        System.out.println("deposit   compliant:    " + depositCompliant(balance, new BigDecimal("25.50")));

        Accounts noncompliant = new Accounts();
        Accounts compliant = new Accounts();
        for (String name : new String[] {"alice", "bob", "alice"}) {
            System.out.printf("register %-6s noncompliant: %-24s compliant: %s%n",
                    name, noncompliant.registerNoncompliant(name), compliant.registerCompliant(name));
        }
    }

    // Noncompliant: String is immutable; the result of replace is thrown away
    static String sanitizeNoncompliant(String fileName) {
        fileName.replace("../", "");
        fileName.replace('/', '_');
        return fileName;
    }

    // Compliant: use the returned values
    static String sanitizeCompliant(String fileName) {
        String cleaned = fileName.replace("../", "");
        cleaned = cleaned.replace('/', '_');
        return cleaned;
    }

    static BigDecimal depositNoncompliant(BigDecimal balance, BigDecimal amount) {
        balance.add(amount); // returns the sum, balance itself never changes
        return balance;
    }

    static BigDecimal depositCompliant(BigDecimal balance, BigDecimal amount) {
        return balance.add(amount);
    }

    static final class Accounts {
        private final Set<String> names = new HashSet<>();

        String registerNoncompliant(String name) {
            names.add(name); // false for a duplicate, but nobody looks
            return "created " + name;
        }

        String registerCompliant(String name) {
            if (!names.add(name)) {
                return "rejected (name taken)";
            }
            return "created " + name;
        }
    }
}

/* Output:
sanitize  noncompliant: ../../etc/cron.d/job
sanitize  compliant:    etc_cron.d_job
deposit   noncompliant: 100.00
deposit   compliant:    125.50
register alice  noncompliant: created alice            compliant: created alice
register bob    noncompliant: created bob              compliant: created bob
register alice  noncompliant: created alice            compliant: rejected (name taken)
*/
