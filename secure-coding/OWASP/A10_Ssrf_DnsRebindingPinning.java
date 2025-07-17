// OWASP Top 10 2021: A10:2021 Server-Side Request Forgery, DNS rebinding and redirects (CWE-918, CWE-367)
// https://owasp.org/Top10/A10_2021-Server-Side_Request_Forgery_%28SSRF%29/
//
// A10_Ssrf_UrlValidation.java validates the URL itself. This file is about
// what happens AFTER validation. "Resolve the host, check that the address
// is public, then hand the URL to the HTTP client" has a time-of-check to
// time-of-use gap: the client resolves the name again. An attacker's DNS
// server answering with TTL 0 returns a public address to the check and
// 127.0.0.1 to the connect (DNS rebinding); an answer with several records
// passes a check of the first one; and a public server can simply reply
// "302 Location: http://169.254.169.254/..." to a client that follows
// redirects on its own. The guard: resolve once, require EVERY returned
// address to be public, connect to that pinned address (sending the
// original name only as the Host header / SNI), turn off automatic
// redirects and send each Location through the same check, with a hop
// limit. Resolver and network are in-memory; nothing leaves the process.
import java.net.InetAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class A10_Ssrf_DnsRebindingPinning {
    // Attacker-controlled zone, TTL 0: every query may get a different answer
    static final class FakeDns {
        static final Map<String, List<List<String>>> ZONE = Map.of(
                "images.partner.example", List.of(List.of("93.184.216.34")),
                "rebind.attacker.example", List.of(List.of("93.184.216.34"), List.of("127.0.0.1")),
                "redirect.attacker.example", List.of(List.of("93.184.216.34")),
                "mixed.attacker.example", List.of(List.of("93.184.216.34", "127.0.0.1")));
        static final Map<String, Integer> queries = new HashMap<>();

        static List<InetAddress> resolve(String host) {
            List<List<String>> answers = ZONE.get(host);
            if (answers == null) return List.of();
            int n = queries.merge(host, 1, Integer::sum);
            return answers.get(Math.min(n, answers.size()) - 1).stream().map(InetAddress::ofLiteral).toList();
        }
    }

    // What each address serves; the public one is the attacker's own web server
    static String serve(InetAddress address, String hostHeader, String path) {
        return switch (address.getHostAddress()) {
            case "93.184.216.34" -> switch (hostHeader) {
                case "images.partner.example" -> "200 image bytes";
                case "redirect.attacker.example" -> "302 http://169.254.169.254/latest/meta-data/iam";
                default -> "200 harmless attacker page";
            };
            case "127.0.0.1" -> "200 INTERNAL ADMIN PANEL";
            case "169.254.169.254" -> "200 CLOUD CREDENTIALS";
            default -> "connection refused";
        };
    }

    static boolean isPublic(InetAddress a) {
        return !(a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress());
    }

    // A typical client: resolves on its own, takes the last record it got, follows redirects
    static String naiveClient(String url, int hops) {
        URI uri = URI.create(url);
        List<InetAddress> addresses = literalOrResolve(uri.getHost());
        if (addresses.isEmpty()) return "unknown host";
        String response = serve(addresses.get(addresses.size() - 1), uri.getHost(), uri.getPath());
        if (response.startsWith("302 ") && hops < 3) return naiveClient(response.substring(4), hops + 1);
        return response;
    }

    // Noncompliant: check the name, then let the client resolve (and redirect) by itself
    static String fetchNoncompliant(String url) {
        List<InetAddress> checked = FakeDns.resolve(URI.create(url).getHost());
        if (checked.isEmpty() || !isPublic(checked.get(0))) return "blocked";
        return naiveClient(url, 0);
    }

    // Compliant: resolve once, check every record, pin, handle redirects ourselves
    static String fetchCompliant(String url) {
        for (int hop = 0; hop <= 3; hop++) {
            URI uri = URI.create(url);
            String host = uri.getHost();
            List<InetAddress> addresses = literalOrResolve(host);
            if (addresses.isEmpty()) return "blocked: unknown host";
            for (InetAddress a : addresses) {
                if (!isPublic(a)) {
                    String target = host.equals(a.getHostAddress()) ? host : host + " -> " + a.getHostAddress();
                    return (hop > 0 ? "redirect blocked: " : "blocked: ") + target + " is not public";
                }
            }
            InetAddress pinned = addresses.get(0);
            String response = serve(pinned, host, uri.getPath()); // connect to the pinned IP, Host: <name>
            if (!response.startsWith("302 ")) return response + " (from pinned " + pinned.getHostAddress() + ")";
            url = response.substring(4);
        }
        return "blocked: too many redirects";
    }

    static List<InetAddress> literalOrResolve(String host) {
        return Character.isDigit(host.charAt(0)) ? List.of(InetAddress.ofLiteral(host)) : FakeDns.resolve(host);
    }

    public static void main(String[] args) {
        for (String url : List.of(
                "https://images.partner.example/cat.png",
                "http://rebind.attacker.example/",
                "http://mixed.attacker.example/",
                "http://redirect.attacker.example/avatar.png")) {
            FakeDns.queries.clear();
            String bad = fetchNoncompliant(url);
            int badQueries = FakeDns.queries.values().stream().mapToInt(Integer::intValue).sum();
            FakeDns.queries.clear();
            String good = fetchCompliant(url);
            int goodQueries = FakeDns.queries.values().stream().mapToInt(Integer::intValue).sum();
            System.out.println(url);
            System.out.println("  noncompliant: " + bad + "  [DNS lookups: " + badQueries + "]");
            System.out.println("  compliant:    " + good + "  [DNS lookups: " + goodQueries + "]");
        }
    }
}

/* Output:
https://images.partner.example/cat.png
  noncompliant: 200 image bytes  [DNS lookups: 2]
  compliant:    200 image bytes (from pinned 93.184.216.34)  [DNS lookups: 1]
http://rebind.attacker.example/
  noncompliant: 200 INTERNAL ADMIN PANEL  [DNS lookups: 2]
  compliant:    200 harmless attacker page (from pinned 93.184.216.34)  [DNS lookups: 1]
http://mixed.attacker.example/
  noncompliant: 200 INTERNAL ADMIN PANEL  [DNS lookups: 2]
  compliant:    blocked: mixed.attacker.example -> 127.0.0.1 is not public  [DNS lookups: 1]
http://redirect.attacker.example/avatar.png
  noncompliant: 200 CLOUD CREDENTIALS  [DNS lookups: 2]
  compliant:    redirect blocked: 169.254.169.254 is not public  [DNS lookups: 1]
*/
