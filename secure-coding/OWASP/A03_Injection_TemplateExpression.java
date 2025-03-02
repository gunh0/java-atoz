// OWASP Top 10 2021: A03:2021 Injection, template / expression language injection (CWE-917, CWE-1336)
// https://owasp.org/Top10/A03_2021-Injection/
//
// Template engines and expression languages (JSP EL, SpEL, OGNL, MVEL,
// Freemarker, ...) evaluate ${...} against a context of objects. They are
// code interpreters: if untrusted text becomes part of the TEMPLATE instead
// of a VALUE, the attacker writes expressions and reads whatever the context
// can reach (secrets, other users' data; in real engines with method calls,
// often remote code execution). Two common ways it happens: building the
// template by concatenation, and evaluating twice (rendering the output of
// a first pass again, e.g. "layout" or "i18n" passes). Keep templates
// constant, pass user input only as variables, evaluate once, and give each
// template a minimal context with an allowlist of paths.
// The evaluator below is a toy that only follows record components; it is
// enough to show the bug class without any real engine.
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class A03_Injection_TemplateExpression {
    record User(String name, String email) {}

    record Order(String id, String total) {}

    record Config(String smtpHost, String dbPassword) {}

    static final Pattern EXPR = Pattern.compile("\\$\\{([A-Za-z][A-Za-z0-9]*(?:\\.[A-Za-z][A-Za-z0-9]*)*)}");

    public static void main(String[] args) throws Exception {
        Config config = new Config("smtp.internal", "pg-Pa55w0rd!");
        Order order = new Order("A-1042", "EUR 59.90");

        for (String displayName : new String[] {"Alice", "${config.dbPassword}"}) {
            User user = new User(displayName, "alice@example.com");
            // One shared, "convenient" context for every template
            Map<String, Object> everything = Map.of("user", user, "order", order, "config", config);
            System.out.println("display name: " + displayName);

            // Noncompliant 1: user input concatenated into the template text
            String template1 = "Hello " + user.name() + ", order ${order.id} ships soon.";
            System.out.println("  concatenated template: " + render(template1, everything, null));

            // Noncompliant 2: user input passed as a value, but the result is rendered again
            String firstPass = render("Hello ${user.name}, order ${order.id} ships soon.", everything, null);
            System.out.println("  rendered twice:        " + render(firstPass, everything, null));

            // Compliant: constant template, one pass, minimal context, allowlisted paths
            Map<String, Object> minimal = Map.of("user", user, "order", order);
            Set<String> allowed = Set.of("user.name", "order.id");
            System.out.println("  compliant:             "
                    + render("Hello ${user.name}, order ${order.id} ships soon.", minimal, allowed));
        }

        // Even a malicious template author is contained by the allowlist
        Map<String, Object> minimal = Map.of("user", new User("Alice", "alice@example.com"), "order", order);
        System.out.println("hostile template, compliant evaluator: "
                + render("${user.email} ${config.dbPassword} ${order.total}", minimal, Set.of("user.name", "order.id")));
    }

    // Replaces each ${path} once; the replacement text is never scanned again
    static String render(String template, Map<String, Object> context, Set<String> allowedPaths) {
        Matcher m = EXPR.matcher(template);
        return m.replaceAll(match -> {
            String path = match.group(1);
            if (allowedPaths != null && !allowedPaths.contains(path)) {
                return "[denied]";
            }
            Object value = resolve(path, context);
            return Matcher.quoteReplacement(value == null ? "[undefined]" : value.toString());
        });
    }

    // Follows a dotted path: a context entry, then record components
    static Object resolve(String path, Map<String, Object> context) {
        String[] parts = path.split("\\.");
        Object current = context.get(parts[0]);
        for (int i = 1; i < parts.length && current != null; i++) {
            current = component(current, parts[i]);
        }
        return current;
    }

    static Object component(Object target, String name) {
        if (!target.getClass().isRecord()) {
            return null;
        }
        for (RecordComponent rc : target.getClass().getRecordComponents()) {
            if (rc.getName().equals(name)) {
                try {
                    return rc.getAccessor().invoke(target);
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            }
        }
        return null;
    }
}

/* Output:
display name: Alice
  concatenated template: Hello Alice, order A-1042 ships soon.
  rendered twice:        Hello Alice, order A-1042 ships soon.
  compliant:             Hello Alice, order A-1042 ships soon.
display name: ${config.dbPassword}
  concatenated template: Hello pg-Pa55w0rd!, order A-1042 ships soon.
  rendered twice:        Hello pg-Pa55w0rd!, order A-1042 ships soon.
  compliant:             Hello ${config.dbPassword}, order A-1042 ships soon.
hostile template, compliant evaluator: [denied] [denied] [denied]
*/
