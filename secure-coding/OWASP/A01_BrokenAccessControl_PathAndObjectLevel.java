// OWASP Top 10 2021: A01:2021 Broken Access Control (CWE-22, CWE-639, CWE-863)
// https://owasp.org/Top10/A01_2021-Broken_Access_Control/
//
// Two classic gaps in one request pipeline.
// 1. Path-based rules checked on the RAW request path, while the router
//    dispatches on a normalized one. Whatever the router treats as equal
//    (/ADMIN, //admin, /public/../admin, %2e%2e, ;matrix parameters, a
//    trailing slash) slips past a string prefix check. Canonicalize once,
//    reject what cannot be canonicalized safely (encoded slashes, leftover
//    percent signs), authorize and route on that same canonical path, and
//    deny by default: a route that no rule allows is not reachable.
// 2. Insecure direct object references (IDOR): /api/invoices/1002 is
//    authorized as "any logged-in user", but the invoice belongs to someone
//    else. Check ownership on every object access, ideally by querying with
//    the owner, and answer "not found" for other users' objects so that ids
//    cannot be enumerated.
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class A01_BrokenAccessControl_PathAndObjectLevel {
    record User(String name, String role) {}

    record Invoice(int id, String owner, String amount) {}

    static final Map<Integer, Invoice> INVOICES = Map.of(
            1001, new Invoice(1001, "mallory", "EUR 12.00"),
            1002, new Invoice(1002, "alice", "EUR 9,800.00"));

    public static void main(String[] args) {
        User mallory = new User("mallory", "USER");
        List<String> paths = List.of(
                "/public/index.html",
                "/admin/users",
                "/ADMIN/users",
                "//admin/users",
                "/public/../admin/users",
                "/public/%2e%2e/admin/users",
                "/admin;jsessionid=1/users",
                "/api/export",
                "/api/export/",
                "/public/..%2fadmin/users",
                "/internal/debug");
        System.out.println("path-based rules, request by " + mallory.name() + " (" + mallory.role() + ")");
        for (String path : paths) {
            System.out.printf("  %-28s noncompliant: %-24s compliant: %s%n",
                    path, noncompliant(path, mallory), compliant(path, mallory));
        }

        System.out.println("object-level checks, GET /api/invoices/{id} by " + mallory.name());
        for (int id : new int[] {1001, 1002, 1003}) {
            System.out.printf("  id %d  noncompliant: %-31s compliant: %s%n",
                    id, invoiceNoncompliant(id), invoiceCompliant(id, mallory));
        }
    }

    // ---- path-based access control ----

    // Noncompliant: a blocklist of protected strings checked on the raw path;
    // everything else is allowed and handed to the (normalizing) router
    static String noncompliant(String rawPath, User user) {
        boolean adminOnly = rawPath.startsWith("/admin/") || rawPath.equals("/api/export");
        if (adminOnly && !user.role().equals("ADMIN")) {
            return "403";
        }
        return route(routerNormalize(rawPath));
    }

    // Compliant: canonicalize once, deny by default, route the same canonical path
    static String compliant(String rawPath, User user) {
        Optional<String> canonical = canonicalize(rawPath);
        if (canonical.isEmpty()) {
            return "400 (cannot canonicalize)";
        }
        String path = canonical.get();
        String required = requiredRole(path);
        if (required == null) {
            return "404 (no rule allows it)";
        }
        if (!required.equals("ANY") && !required.equals(user.role())
                && !(required.equals("USER") && user.role().equals("ADMIN"))) {
            return "403";
        }
        return route(path);
    }

    // Allowlist of route rules; anything not listed is denied
    static String requiredRole(String path) {
        if (path.startsWith("/public/")) {
            return "ANY";
        }
        if (path.equals("/admin") || path.startsWith("/admin/") || path.equals("/api/export")) {
            return "ADMIN";
        }
        if (path.matches("/api/invoices/\\d+")) {
            return "USER";
        }
        return null;
    }

    static Optional<String> canonicalize(String rawPath) {
        String lower = rawPath.toLowerCase(Locale.ROOT);
        if (lower.contains("%2f") || lower.contains("%5c") || rawPath.contains("\\")) {
            return Optional.empty(); // an encoded separator has no safe meaning
        }
        String decoded = URLDecoder.decode(rawPath.replace("+", "%2B"), StandardCharsets.UTF_8);
        if (decoded.contains("%")) {
            return Optional.empty(); // double encoding
        }
        return Optional.of(normalizeSegments(decoded));
    }

    // What the framework does before dispatching: decode, drop ;params,
    // collapse //, resolve . and .., ignore case and the trailing slash
    static String routerNormalize(String rawPath) {
        return normalizeSegments(URLDecoder.decode(rawPath.replace("+", "%2B"), StandardCharsets.UTF_8));
    }

    static String normalizeSegments(String path) {
        Deque<String> out = new ArrayDeque<>();
        for (String segment : path.split("/")) {
            int semicolon = segment.indexOf(';');
            if (semicolon >= 0) {
                segment = segment.substring(0, semicolon);
            }
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                out.pollLast();
            } else {
                out.addLast(segment.toLowerCase(Locale.ROOT));
            }
        }
        return "/" + String.join("/", out);
    }

    static String route(String normalized) {
        if (normalized.startsWith("/admin/")) {
            return "200 ADMIN PAGE";
        }
        if (normalized.equals("/api/export")) {
            return "200 FULL DB EXPORT";
        }
        if (normalized.equals("/internal/debug")) {
            return "200 DEBUG CONSOLE";
        }
        if (normalized.startsWith("/public/")) {
            return "200 public page";
        }
        return "404";
    }

    // ---- object-level access control ----

    // Noncompliant: the id from the URL is trusted; any logged-in user reads any invoice
    static String invoiceNoncompliant(int id) {
        Invoice invoice = INVOICES.get(id);
        return invoice == null ? "404" : "200 " + invoice.amount() + " (owner " + invoice.owner() + ")";
    }

    // Compliant: look up by id AND owner; other users' invoices look like missing ones
    static String invoiceCompliant(int id, User user) {
        return Optional.ofNullable(INVOICES.get(id))
                .filter(invoice -> invoice.owner().equals(user.name()))
                .map(invoice -> "200 " + invoice.amount())
                .orElse("404");
    }
}

/* Output:
path-based rules, request by mallory (USER)
  /public/index.html           noncompliant: 200 public page          compliant: 200 public page
  /admin/users                 noncompliant: 403                      compliant: 403
  /ADMIN/users                 noncompliant: 200 ADMIN PAGE           compliant: 403
  //admin/users                noncompliant: 200 ADMIN PAGE           compliant: 403
  /public/../admin/users       noncompliant: 200 ADMIN PAGE           compliant: 403
  /public/%2e%2e/admin/users   noncompliant: 200 ADMIN PAGE           compliant: 403
  /admin;jsessionid=1/users    noncompliant: 200 ADMIN PAGE           compliant: 403
  /api/export                  noncompliant: 403                      compliant: 403
  /api/export/                 noncompliant: 200 FULL DB EXPORT       compliant: 403
  /public/..%2fadmin/users     noncompliant: 200 ADMIN PAGE           compliant: 400 (cannot canonicalize)
  /internal/debug              noncompliant: 200 DEBUG CONSOLE        compliant: 404 (no rule allows it)
object-level checks, GET /api/invoices/{id} by mallory
  id 1001  noncompliant: 200 EUR 12.00 (owner mallory)   compliant: 200 EUR 12.00
  id 1002  noncompliant: 200 EUR 9,800.00 (owner alice)  compliant: 404
  id 1003  noncompliant: 404                             compliant: 404
*/
