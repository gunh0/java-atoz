// IDS03-J. Do not log unsanitized user input
// https://wiki.sei.cmu.edu/confluence/display/java/IDS03-J.+Do+not+log+unsanitized+user+input
//
// Log files are evidence: incident responders and alerting tools read them
// line by line. If user input is written to a log as is, a value containing
// a line break can end the current entry and forge a complete, legitimate
// looking new one ("User admin logged in"), hide an attack, or inject
// markup into a log viewer. Validate input against what it should look like
// (a user name has no control characters), or neutralize it before logging
// by escaping CR, LF and other control characters.
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class IDS03_LogInjection {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.-]{1,32}");

    public static void main(String[] args) {
        String[] attempts = {
                "alice",
                "guest\nINFO  login succeeded for user=admin",
                "bob\r\nWARN  audit log rotated, previous entries removed",
        };

        Log noncompliant = new Log();
        for (String user : attempts) {
            loginNoncompliant(noncompliant, user);
        }
        System.out.println("noncompliant log (" + noncompliant.lines().size() + " lines for 3 attempts):");
        noncompliant.lines().forEach(l -> System.out.println("  " + l));

        Log escaped = new Log();
        for (String user : attempts) {
            loginCompliantEscape(escaped, user);
        }
        System.out.println("compliant log, escaped (" + escaped.lines().size() + " lines):");
        escaped.lines().forEach(l -> System.out.println("  " + l));

        Log validated = new Log();
        for (String user : attempts) {
            loginCompliantValidate(validated, user);
        }
        System.out.println("compliant log, validated (" + validated.lines().size() + " lines):");
        validated.lines().forEach(l -> System.out.println("  " + l));
    }

    // Noncompliant: raw input goes straight into the log
    static void loginNoncompliant(Log log, String user) {
        log.write("WARN  login failed for user=" + user);
    }

    // Compliant: neutralize control characters before logging
    static void loginCompliantEscape(Log log, String user) {
        log.write("WARN  login failed for user=" + escape(user));
    }

    // Compliant: reject input that is not a valid user name at all
    static void loginCompliantValidate(Log log, String user) {
        if (!USERNAME.matcher(user).matches()) {
            log.write("WARN  login failed: malformed user name (" + user.length() + " chars)");
            return;
        }
        log.write("WARN  login failed for user=" + user);
    }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                default -> sb.append(Character.isISOControl(c) ? String.format("\\u%04x", (int) c) : String.valueOf(c));
            }
        }
        return sb.toString();
    }

    // A stand-in for a log file: appends text, and a reader splits it into lines
    static final class Log {
        private final StringBuilder file = new StringBuilder();

        void write(String entry) {
            file.append(entry).append('\n');
        }

        List<String> lines() {
            return new ArrayList<>(file.toString().lines().toList());
        }
    }
}

/* Output:
noncompliant log (5 lines for 3 attempts):
  WARN  login failed for user=alice
  WARN  login failed for user=guest
  INFO  login succeeded for user=admin
  WARN  login failed for user=bob
  WARN  audit log rotated, previous entries removed
compliant log, escaped (3 lines):
  WARN  login failed for user=alice
  WARN  login failed for user=guest\nINFO  login succeeded for user=admin
  WARN  login failed for user=bob\r\nWARN  audit log rotated, previous entries removed
compliant log, validated (3 lines):
  WARN  login failed for user=alice
  WARN  login failed: malformed user name (42 chars)
  WARN  login failed: malformed user name (54 chars)
*/
