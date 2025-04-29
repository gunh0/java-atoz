// OWASP Top 10 2021: A06:2021 Vulnerable and Outdated Components (CWE-1104, CWE-1035)
// https://owasp.org/Top10/A06_2021-Vulnerable_and_Outdated_Components/
//
// Most of the code in a Java service is in its dependencies, and most of
// those are transitive: pulled in by a library you chose, never listed in
// your own build file. Checking them against advisories is routine, but
// home-grown checks fail in quiet ways: comparing versions as strings
// ("1.10.0" sorts before "1.9.0"), matching only the exact versions named
// in an advisory instead of its affected RANGE, or scanning only direct
// dependencies. Parse versions numerically, evaluate ranges, and scan the
// resolved dependency tree. (In practice use a maintained scanner such as
// OWASP Dependency-Check against a real advisory feed; the advisory IDs and
// packages below are fictional, made up for this example.)
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class A06_VulnerableComponents_AdvisoryCheck {
    // Resolved dependency tree: "  " indentation marks transitive dependencies
    static final String DEPENDENCIES = """
            org.example:web-framework:3.2.0
              org.example:template-engine:1.10.2
              org.example:xml-binding:2.4.1
                org.example:xml-parser:0.9.12
            org.example:json-codec:2.13.0
            org.example:http-client:5.1.9
            """;

    // Fictional advisories: affected range [from, fixedIn)
    record Advisory(String id, String artifact, String from, String fixedIn, String summary) { }

    static final List<Advisory> ADVISORIES = List.of(
            new Advisory("DEMO-2025-0001", "org.example:template-engine", "1.0.0", "1.10.5", "expression injection in templates"),
            new Advisory("DEMO-2025-0002", "org.example:xml-parser", "0.9.0", "0.9.15", "external entities enabled by default"),
            new Advisory("DEMO-2025-0003", "org.example:json-codec", "2.0.0", "2.9.0", "polymorphic deserialization gadget"),
            new Advisory("DEMO-2025-0004", "org.example:http-client", "5.0.0", "5.1.10", "hostname verification skipped on redirect"));

    record Dependency(String artifact, String version, boolean direct) { }

    static List<Dependency> parse(String tree) {
        List<Dependency> deps = new ArrayList<>();
        for (String line : tree.lines().filter(l -> !l.isBlank()).toList()) {
            String coords = line.strip();
            int lastColon = coords.lastIndexOf(':');
            deps.add(new Dependency(coords.substring(0, lastColon), coords.substring(lastColon + 1), !line.startsWith(" ")));
        }
        return deps;
    }

    // Noncompliant: direct dependencies only, versions compared as strings
    static List<String> scanNoncompliant(List<Dependency> deps) {
        List<String> findings = new ArrayList<>();
        for (Dependency d : deps) {
            if (!d.direct()) continue;
            for (Advisory a : ADVISORIES) {
                if (a.artifact().equals(d.artifact())
                        && d.version().compareTo(a.from()) >= 0 && d.version().compareTo(a.fixedIn()) < 0) {
                    findings.add(a.id() + " " + d.artifact() + ":" + d.version());
                }
            }
        }
        return findings;
    }

    // Compliant: every resolved dependency, numeric version comparison over the range
    static List<String> scanCompliant(List<Dependency> deps) {
        List<String> findings = new ArrayList<>();
        for (Dependency d : deps) {
            for (Advisory a : ADVISORIES) {
                if (a.artifact().equals(d.artifact())
                        && compareVersions(d.version(), a.from()) >= 0
                        && compareVersions(d.version(), a.fixedIn()) < 0) {
                    findings.add(a.id() + " " + d.artifact() + ":" + d.version()
                            + (d.direct() ? "" : " (transitive)") + ", upgrade to " + a.fixedIn() + ": " + a.summary());
                }
            }
        }
        return findings;
    }

    // Numeric, component-wise: 1.10.0 > 1.9.0, and 2.4 == 2.4.0
    static int compareVersions(String a, String b) {
        int[] x = Arrays.stream(a.split("\\.")).mapToInt(Integer::parseInt).toArray();
        int[] y = Arrays.stream(b.split("\\.")).mapToInt(Integer::parseInt).toArray();
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int c = Integer.compare(i < x.length ? x[i] : 0, i < y.length ? y[i] : 0);
            if (c != 0) return c;
        }
        return 0;
    }

    public static void main(String[] args) {
        System.out.println("string compare:  \"1.10.0\".compareTo(\"1.9.0\") = " + Integer.signum("1.10.0".compareTo("1.9.0"))
                + " (claims 1.10.0 is older)");
        System.out.println("numeric compare: compareVersions(\"1.10.0\", \"1.9.0\") = " + compareVersions("1.10.0", "1.9.0"));

        List<Dependency> deps = parse(DEPENDENCIES);
        System.out.println("dependencies: " + deps.size() + " (" + deps.stream().filter(Dependency::direct).count() + " direct)");

        List<String> bad = scanNoncompliant(deps);
        System.out.println("noncompliant scan: " + bad.size() + " finding(s)");
        bad.forEach(f -> System.out.println("  " + f));
        System.out.println("  -> a false positive (2.13.0 is newer than the fix 2.9.0), and the real issues missed");

        List<String> good = scanCompliant(deps);
        System.out.println("compliant scan: " + good.size() + " finding(s)");
        good.forEach(f -> System.out.println("  " + f));
    }
}

/* Output:
string compare:  "1.10.0".compareTo("1.9.0") = -1 (claims 1.10.0 is older)
numeric compare: compareVersions("1.10.0", "1.9.0") = 1
dependencies: 6 (3 direct)
noncompliant scan: 1 finding(s)
  DEMO-2025-0003 org.example:json-codec:2.13.0
  -> a false positive (2.13.0 is newer than the fix 2.9.0), and the real issues missed
compliant scan: 3 finding(s)
  DEMO-2025-0001 org.example:template-engine:1.10.2 (transitive), upgrade to 1.10.5: expression injection in templates
  DEMO-2025-0002 org.example:xml-parser:0.9.12 (transitive), upgrade to 0.9.15: external entities enabled by default
  DEMO-2025-0004 org.example:http-client:5.1.9, upgrade to 5.1.10: hostname verification skipped on redirect
*/
