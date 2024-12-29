// OWASP Top 10 2021: A10:2021 Server-Side Request Forgery (CWE-918)
// https://owasp.org/Top10/A10_2021-Server-Side_Request_Forgery_%28SSRF%29/
//
// Features that fetch a user-supplied URL (webhooks, link previews, image
// imports) run inside the network perimeter. Without validation, a URL can
// point the server at its cloud metadata endpoint (169.254.169.254), an
// admin port on localhost or an internal service. String blocklists fail:
// 127.0.0.1 is also 2130706433, 0177.0.0.1 (octal, for C-based clients),
// [::ffff:127.0.0.1] and [::1], and a harmless-looking DNS name can resolve
// to an internal address, or to a public one at check time and an internal
// one at connect time (DNS rebinding). Validate the PARSED URL: allow only
// http(s), no user-info, resolve the host once, reject any non-public
// address, then connect to exactly that address. This program uses an
// in-memory resolver and never opens a connection.
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class A10_Ssrf_UrlValidation {
    public static void main(String[] args) {
        List<String> urls = List.of(
                "https://hooks.partner.example/notify",
                "http://169.254.169.254/latest/meta-data/",
                "http://2130706433/admin",
                "http://0177.0.0.1/admin",
                "http://[::ffff:127.0.0.1]/admin",
                "http://[::1]:8080/actuator",
                "http://metadata.attacker.example/",
                "http://rebind.attacker.example/",
                "http://hooks.partner.example@10.0.0.5/",
                "file:///etc/passwd");

        for (String url : urls) {
            FakeDns.reset();
            String bad = fetchNoncompliant(url);
            FakeDns.reset();
            String good = fetchCompliant(url);
            System.out.println(url);
            System.out.println("  noncompliant: " + bad);
            System.out.println("  compliant:    " + good);
        }
    }

    // Noncompliant: string blocklist on the host, then the HTTP client resolves again
    static String fetchNoncompliant(String url) {
        String host = URI.create(url).getHost();
        if (host != null && (host.equals("localhost") || host.equals("127.0.0.1")
                || host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("169.254."))) {
            return "blocked";
        }
        if (host != null && !host.startsWith("[")) {
            FakeDns.resolve(host); // "check" that the name exists
        }
        return "would connect to " + connectTarget(host);
    }

    // Compliant: parse, restrict, resolve once, check every property of the address, pin it
    static String fetchCompliant(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return "blocked: unparsable";
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) return "blocked: scheme " + scheme;
        if (uri.getRawUserInfo() != null) return "blocked: user-info in URL";
        String host = uri.getHost();
        if (host == null) return "blocked: no host";

        InetAddress address;
        if (host.startsWith("[")) {
            address = InetAddress.ofLiteral(host);
        } else if (NUMERIC_HOST.matcher(host).matches()) {
            // Numeric host: Java and POSIX parsers must agree on what it means
            InetAddress java = parseOrNull(host, false), posix = parseOrNull(host, true);
            if (java == null || !java.equals(posix)) return "blocked: ambiguous IP literal";
            address = java;
        } else {
            address = FakeDns.resolve(host); // resolve ONCE
            if (address == null) return "blocked: unknown host";
        }
        String reason = nonPublicReason(address);
        if (reason != null) return "blocked: " + reason + " " + address.getHostAddress();
        return "would connect to " + address.getHostAddress() + " (pinned, Host: " + host + ")";
    }

    // One to four dot-separated decimal, octal or 0x-hex parts: anything inet_aton() might accept
    static final Pattern NUMERIC_HOST = Pattern.compile("(0[xX][0-9a-fA-F]*|[0-9]+)(\\.(0[xX][0-9a-fA-F]*|[0-9]+)){0,3}");

    static String nonPublicReason(InetAddress a) {
        if (a.isAnyLocalAddress()) return "wildcard";
        if (a.isLoopbackAddress()) return "loopback";
        if (a.isLinkLocalAddress()) return "link-local";
        if (a.isSiteLocalAddress()) return "private";
        if (a.isMulticastAddress()) return "multicast";
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address && (b[0] == 0 || (b[0] == 100 && (b[1] & 0xC0) == 64))) return "reserved";
        if (a instanceof Inet6Address && (b[0] & 0xFE) == 0xFC) return "unique-local";
        return null;
    }

    static InetAddress parseOrNull(String host, boolean posix) {
        try {
            return posix ? Inet4Address.ofPosixLiteral(host) : Inet4Address.ofLiteral(host);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // How a typical HTTP client (C library rules for IPv4 literals) picks the address at connect time
    static String connectTarget(String host) {
        if (host == null) return "(local file)";
        if (host.startsWith("[")) return InetAddress.ofLiteral(host).getHostAddress();
        InetAddress literal = parseOrNull(host, true);
        if (literal != null) return literal.getHostAddress();
        InetAddress resolved = FakeDns.resolve(host); // second lookup
        return resolved == null ? "(unknown host)" : resolved.getHostAddress();
    }

    // In-memory DNS. rebind.attacker.example answers public first, loopback afterwards.
    static final class FakeDns {
        private static final Map<String, List<String>> ANSWERS = Map.of(
                "hooks.partner.example", List.of("93.184.216.34"),
                "metadata.attacker.example", List.of("169.254.169.254"),
                "rebind.attacker.example", List.of("93.184.216.34", "127.0.0.1"));
        private static final Map<String, Integer> QUERIES = new HashMap<>();

        static void reset() {
            QUERIES.clear();
        }

        static InetAddress resolve(String name) {
            List<String> answers = ANSWERS.get(name.toLowerCase(Locale.ROOT));
            if (answers == null) return null;
            int n = QUERIES.merge(name, 1, Integer::sum);
            return InetAddress.ofLiteral(answers.get(Math.min(n, answers.size()) - 1));
        }
    }
}

/* Output:
https://hooks.partner.example/notify
  noncompliant: would connect to 93.184.216.34
  compliant:    would connect to 93.184.216.34 (pinned, Host: hooks.partner.example)
http://169.254.169.254/latest/meta-data/
  noncompliant: blocked
  compliant:    blocked: link-local 169.254.169.254
http://2130706433/admin
  noncompliant: would connect to 127.0.0.1
  compliant:    blocked: loopback 127.0.0.1
http://0177.0.0.1/admin
  noncompliant: would connect to 127.0.0.1
  compliant:    blocked: ambiguous IP literal
http://[::ffff:127.0.0.1]/admin
  noncompliant: would connect to 127.0.0.1
  compliant:    blocked: loopback 127.0.0.1
http://[::1]:8080/actuator
  noncompliant: would connect to 0:0:0:0:0:0:0:1
  compliant:    blocked: loopback 0:0:0:0:0:0:0:1
http://metadata.attacker.example/
  noncompliant: would connect to 169.254.169.254
  compliant:    blocked: link-local 169.254.169.254
http://rebind.attacker.example/
  noncompliant: would connect to 127.0.0.1
  compliant:    would connect to 93.184.216.34 (pinned, Host: rebind.attacker.example)
http://hooks.partner.example@10.0.0.5/
  noncompliant: blocked
  compliant:    blocked: user-info in URL
file:///etc/passwd
  noncompliant: would connect to (local file)
  compliant:    blocked: scheme file
*/
