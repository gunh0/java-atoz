// java.net.URI parses and builds identifiers following RFC 3986:
//   scheme://userInfo@host:port/path?query#fragment
// It only parses; nothing is fetched. Prefer it over java.net.URL, whose
// constructors are deprecated since Java 20 and whose equals() may resolve
// host names over the network. Convert with uri.toURL() when an API needs a URL.
//  - getRawXxx() return the text as written (still percent-encoded),
//    getXxx() decode it
//  - the multi-argument constructors quote illegal characters for you
//  - normalize() removes "." and ".." segments, resolve() applies a relative
//    reference to a base, relativize() is the inverse
// For allow-list checks compare scheme and host from the parsed URI, never
// with startsWith() on the raw string (see the last lines below).
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Locale;

public class UriExample {
    static boolean isAllowed(String target) {
        try {
            URI uri = new URI(target);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "api.example.com".equalsIgnoreCase(uri.getHost())
                    && uri.getRawUserInfo() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static void main(String[] args) throws URISyntaxException, MalformedURLException {
        URI uri = URI.create("https://ops:secret@api.example.com:8443/v1/search/../users?q=port%3D22%20open&limit=5#top");
        System.out.println("scheme    = " + uri.getScheme());
        System.out.println("userInfo  = " + uri.getUserInfo());
        System.out.println("host      = " + uri.getHost());
        System.out.println("port      = " + uri.getPort());
        System.out.println("path      = " + uri.getPath());
        System.out.println("rawQuery  = " + uri.getRawQuery());
        System.out.println("query     = " + uri.getQuery());
        System.out.println("fragment  = " + uri.getFragment());
        System.out.println("normalize = " + uri.normalize().getPath());

        // Building: the constructor percent-encodes illegal characters
        URI built = new URI("https", null, "files.example.com", -1, "/reports/Q1 summary.pdf", "lang=ko", null);
        System.out.println("built     = " + built.toASCIIString());

        // Resolving relative references against a base, as a browser does for links
        URI base = URI.create("https://docs.example.com/guide/install/linux.html");
        for (String ref : List.of("macos.html", "../faq.html", "/index.html", "//cdn.example.com/x.js")) {
            System.out.printf("  resolve %-24s -> %s%n", ref, base.resolve(ref));
        }
        System.out.println("  relativize -> " + URI.create("https://docs.example.com/guide/")
                .relativize(URI.create("https://docs.example.com/guide/install/linux.html")));

        // URI -> URL only when an older API requires it
        URL url = uri.toURL();
        System.out.println("as URL: protocol=" + url.getProtocol() + " defaultPort=" + url.getDefaultPort());

        try {
            new URI("https://example.com/a b");
        } catch (URISyntaxException e) {
            System.out.println("invalid: " + e.getReason() + " at index " + e.getIndex());
        }

        // Prefix checks are not URL validation
        List<String> targets = List.of(
                "https://api.example.com/v1/users",
                "https://api.example.com.evil.test/v1/users",
                "https://api.example.com@evil.test/v1/users",
                "HTTPS://API.EXAMPLE.COM/v1/users");
        for (String t : targets) {
            System.out.printf("  %-44s startsWith=%-5b parsed=%b%n", t,
                    t.toLowerCase(Locale.ROOT).startsWith("https://api.example.com"), isAllowed(t));
        }
    }
}

/* Output:
scheme    = https
userInfo  = ops:secret
host      = api.example.com
port      = 8443
path      = /v1/search/../users
rawQuery  = q=port%3D22%20open&limit=5
query     = q=port=22 open&limit=5
fragment  = top
normalize = /v1/users
built     = https://files.example.com/reports/Q1%20summary.pdf?lang=ko
  resolve macos.html               -> https://docs.example.com/guide/install/macos.html
  resolve ../faq.html              -> https://docs.example.com/guide/faq.html
  resolve /index.html              -> https://docs.example.com/index.html
  resolve //cdn.example.com/x.js   -> https://cdn.example.com/x.js
  relativize -> install/linux.html
as URL: protocol=https defaultPort=443
invalid: Illegal character in path at index 21
  https://api.example.com/v1/users             startsWith=true  parsed=true
  https://api.example.com.evil.test/v1/users   startsWith=true  parsed=false
  https://api.example.com@evil.test/v1/users   startsWith=true  parsed=false
  HTTPS://API.EXAMPLE.COM/v1/users             startsWith=true  parsed=true
*/
