// OWASP Top 10 2021: A01:2021 Broken Access Control, open redirect (CWE-601)
// https://owasp.org/Top10/A01_2021-Broken_Access_Control/
//
// Login pages and link trackers often take the destination from the
// request: /login?next=/account. If any value is accepted, a link on the
// real domain (/login?next=https://evil.example) sends users to a phishing
// page after they authenticated, and in OAuth flows can leak codes and
// tokens to the attacker. String checks are easy to get wrong: browsers
// treat //host and /\host as "another host", strip tabs and newlines, and
// read https://shop.example@evil.net as a login to evil.net. Prefer
// redirecting to a server-side id ("next=cart"); otherwise accept only
// same-site relative paths, or absolute URLs whose parsed scheme and host
// are on an exact allowlist.
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class A01_BrokenAccessControl_OpenRedirect {
    static final Set<String> ALLOWED_HOSTS = Set.of("shop.example", "help.shop.example");

    public static void main(String[] args) {
        List<String> nexts = List.of(
                "/account",
                "/orders?id=7#top",
                "https://help.shop.example/faq",
                "//evil.example/login",
                "/\\evil.example",
                "/\t/evil.example",
                "///evil.example",
                "https://shop.example.evil.net/",
                "https://shop.example@evil.net/",
                "javascript:alert(document.cookie)",
                "http://shop.example/account");

        System.out.printf("%-36s %-14s %s%n", "next=", "noncompliant", "compliant");
        for (String next : nexts) {
            System.out.printf("%-36s %-14s %s%n", visible(next), verdict(redirectNoncompliant(next)), verdict(redirectCompliant(next)));
        }

        // Best: the client names a destination, the server owns the URL
        Map<String, String> destinations = Map.of("cart", "/cart", "account", "/account");
        System.out.println("next=cart -> " + destinations.getOrDefault("cart", "/"));
        System.out.println("next=https://evil.example -> " + destinations.getOrDefault("https://evil.example", "/"));
    }

    // Noncompliant: prefix checks on the raw string
    static String redirectNoncompliant(String next) {
        if (next.startsWith("/") || next.startsWith("https://shop.example")) {
            return next;
        }
        return "/";
    }

    // Compliant: parse, then allow relative paths or allowlisted https hosts
    static String redirectCompliant(String next) {
        for (char c : next.toCharArray()) {
            if (c < 0x21 || c > 0x7E || c == '\\') {
                return "/"; // control characters, spaces, backslashes, non-ASCII
            }
        }
        URI uri;
        try {
            uri = new URI(next);
        } catch (URISyntaxException e) {
            return "/";
        }
        if (uri.getScheme() == null && uri.getRawAuthority() == null) {
            // relative: must be an absolute path on this site, not "//host" or "///host"
            return next.startsWith("/") && !next.startsWith("//") ? next : "/";
        }
        boolean httpsAllowedHost = "https".equalsIgnoreCase(uri.getScheme())
                && uri.getRawUserInfo() == null
                && uri.getHost() != null
                && ALLOWED_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
                && (uri.getPort() == -1 || uri.getPort() == 443);
        return httpsAllowedHost ? next : "/";
    }

    static String verdict(String target) {
        return target.equals("/") ? "home" : "redirected";
    }

    static String visible(String s) {
        return s.replace("\t", "\\t");
    }
}

/* Output:
next=                                noncompliant   compliant
/account                             redirected     redirected
/orders?id=7#top                     redirected     redirected
https://help.shop.example/faq        home           redirected
//evil.example/login                 redirected     home
/\evil.example                       redirected     home
/\t/evil.example                     redirected     home
///evil.example                      redirected     home
https://shop.example.evil.net/       redirected     home
https://shop.example@evil.net/       redirected     home
javascript:alert(document.cookie)    home           home
http://shop.example/account          home           home
next=cart -> /cart
next=https://evil.example -> /
*/
