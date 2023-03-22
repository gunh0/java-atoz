// Template Method (behavioral): define the skeleton of an algorithm in an
// operation, deferring some steps to subclasses. Subclasses redefine those
// steps without changing the algorithm's structure.
//
// Here every configuration audit runs the same way: collect settings, check
// each one, optionally fix what failed, then print a report. That sequence is
// the final template method run(); subclasses only supply the varying steps
// (collect, check) and may override a hook (autoFix, which defaults to "no").
// The base class calls the subclass ("don't call us, we'll call you"), so no
// audit can forget a step or run them in the wrong order.
// JDK examples: AbstractList (get/size are the steps, iterator/indexOf/equals
// are templates), InputStream.read(byte[]) built on read(), HttpServlet.service.
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class TemplateMethodDemo {
    record Finding(String key, String actual, String expected) {}

    abstract static class ConfigAudit {
        // The template method: final, so subclasses cannot reorder the steps
        public final void run(String host) {
            System.out.println("== " + name() + " on " + host);
            Map<String, String> settings = collect(host);
            List<Finding> findings = new ArrayList<>();
            settings.forEach((key, value) -> {
                String expected = expected(key);
                if (expected != null && !expected.equals(value)) findings.add(new Finding(key, value, expected));
            });
            if (!findings.isEmpty() && autoFix()) {
                for (Finding f : findings) {
                    settings.put(f.key(), f.expected());
                    System.out.println("  fixed " + f.key() + ": " + f.actual() + " -> " + f.expected());
                }
                findings.clear();
            }
            report(settings.size(), findings);
        }

        // Primitive operations: every audit must provide them
        protected abstract String name();
        protected abstract Map<String, String> collect(String host);
        protected abstract String expected(String key);

        // Hook: a default the subclass may override
        protected boolean autoFix() { return false; }

        // A concrete step shared by all audits
        private void report(int checked, List<Finding> findings) {
            System.out.println("  checked " + checked + " settings, " + findings.size() + " finding(s)");
            for (Finding f : findings) {
                System.out.println("  FAIL " + f.key() + "=" + f.actual() + " (expected " + f.expected() + ")");
            }
        }
    }

    static final class SshdAudit extends ConfigAudit {
        protected String name() { return "sshd audit"; }

        protected Map<String, String> collect(String host) {
            return new TreeMap<>(Map.of("PermitRootLogin", "yes", "PasswordAuthentication", "yes", "Port", "22"));
        }

        protected String expected(String key) {
            return switch (key) {
                case "PermitRootLogin", "PasswordAuthentication" -> "no";
                default -> null; // not audited
            };
        }
    }

    static final class TlsAudit extends ConfigAudit {
        protected String name() { return "tls audit"; }

        protected Map<String, String> collect(String host) {
            return new TreeMap<>(Map.of("min_version", "TLSv1.0", "hsts", "on", "compression", "on"));
        }

        protected String expected(String key) {
            return switch (key) {
                case "min_version" -> "TLSv1.2";
                case "compression" -> "off";
                case "hsts" -> "on";
                default -> null;
            };
        }

        // Overriding the hook changes behaviour without touching run()
        @Override
        protected boolean autoFix() { return true; }
    }

    public static void main(String[] args) {
        List<ConfigAudit> audits = List.of(new SshdAudit(), new TlsAudit());
        for (ConfigAudit audit : audits) audit.run("web-01");
    }
}

/* Output:
== sshd audit on web-01
  checked 3 settings, 2 finding(s)
  FAIL PasswordAuthentication=yes (expected no)
  FAIL PermitRootLogin=yes (expected no)
== tls audit on web-01
  fixed compression: on -> off
  fixed min_version: TLSv1.0 -> TLSv1.2
  checked 3 settings, 0 finding(s)
*/
