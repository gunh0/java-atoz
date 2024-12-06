// OWASP Top 10 2021: A05:2021 Security Misconfiguration, cookie attributes (CWE-614, CWE-1004)
// https://owasp.org/Top10/A05_2021-Security_Misconfiguration/
//
// A session cookie is a bearer credential, and its protection is entirely
// in the attributes the server sets:
//   Secure    - only sent over HTTPS, never in clear text
//   HttpOnly  - not readable from JavaScript, so an XSS bug cannot steal it
//   SameSite  - Lax or Strict keeps it off most cross-site requests (CSRF)
//   Path=/ and no Domain - scoped to exactly this host
//   __Host- prefix - browsers enforce Secure, Path=/ and no Domain for it
// A bare "Set-Cookie: SESSIONID=..." has none of these. This program runs
// a JDK HttpServer on a loopback port with two login endpoints, fetches
// both with HttpClient, and audits the Set-Cookie headers it receives.
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class A05_SecurityMisconfiguration_CookieFlags {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        // Noncompliant: no attributes at all
        server.createContext("/login-noncompliant", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "SESSIONID=demo-session-value");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        // Compliant: hardened session cookie
        server.createContext("/login-compliant", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie",
                    "__Host-SESSIONID=demo-session-value; Path=/; Secure; HttpOnly; SameSite=Lax; Max-Age=1800");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try (HttpClient client = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build()) {
            for (String path : List.of("/login-noncompliant", "/login-compliant")) {
                URI uri = URI.create("http://" + server.getAddress().getAddress().getHostAddress()
                        + ":" + server.getAddress().getPort() + path);
                HttpResponse<Void> response = client.send(HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.noBody()).build(),
                        HttpResponse.BodyHandlers.discarding());
                for (String header : response.headers().allValues("set-cookie")) {
                    System.out.println("POST " + path + " -> " + response.statusCode());
                    System.out.println("  Set-Cookie: " + header.replace("demo-session-value", "<value>"));
                    List<String> problems = audit(header);
                    System.out.println("  audit: " + (problems.isEmpty() ? "ok" : String.join("; ", problems)));
                }
            }
        } finally {
            server.stop(0);
        }
    }

    // Checks a Set-Cookie header against the rules above
    static List<String> audit(String setCookie) {
        String[] parts = setCookie.split(";");
        String name = parts[0].split("=", 2)[0].trim();
        boolean secure = false, httpOnly = false, pathRoot = false, domain = false;
        String sameSite = null;
        for (int i = 1; i < parts.length; i++) {
            String[] kv = parts[i].trim().split("=", 2);
            String key = kv[0].toLowerCase(Locale.ROOT);
            String value = kv.length > 1 ? kv[1] : "";
            switch (key) {
                case "secure" -> secure = true;
                case "httponly" -> httpOnly = true;
                case "path" -> pathRoot = value.equals("/");
                case "domain" -> domain = true;
                case "samesite" -> sameSite = value.toLowerCase(Locale.ROOT);
                default -> { }
            }
        }
        List<String> problems = new ArrayList<>();
        if (!secure) problems.add("missing Secure");
        if (!httpOnly) problems.add("missing HttpOnly");
        if (sameSite == null) problems.add("missing SameSite");
        else if (sameSite.equals("none")) problems.add("SameSite=None");
        if (!name.startsWith("__Host-")) problems.add("no __Host- prefix");
        else if (!secure || !pathRoot || domain) problems.add("__Host- rules violated");
        return problems;
    }
}

/* Output:
POST /login-noncompliant -> 204
  Set-Cookie: SESSIONID=<value>
  audit: missing Secure; missing HttpOnly; missing SameSite; no __Host- prefix
POST /login-compliant -> 204
  Set-Cookie: __Host-SESSIONID=<value>; Path=/; Secure; HttpOnly; SameSite=Lax; Max-Age=1800
  audit: ok
*/
