// InetAddress.ofLiteral, Inet4Address.ofLiteral, Inet6Address.ofLiteral (Java 22)
//
// InetAddress.getByName(s) does two jobs: it parses s if it is an IP
// literal, and otherwise sends a DNS query for it. Code that only wants to
// validate or normalize an address (an allowlist, a config value, a log
// field) could therefore trigger network lookups, stall on a slow resolver,
// or resolve an attacker-chosen host name. Java 22 adds ofLiteral factory
// methods that only parse: a literal yields an address, anything else is
// an IllegalArgumentException, and no lookup ever happens. Comparing the
// parsed address (not the string) also catches the alternative spellings
// IPv4 allows, such as "127.1" or "2130706433".
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;

public class InetAddressOfLiteral {
    public static void main(String[] args) {
        List<String> inputs = List.of(
                "127.0.0.1", "127.1", "2130706433", "10.0.0.7",
                "::1", "[::1]", "::ffff:127.0.0.1",
                "256.1.1.1", "localhost", "internal.example");

        for (String s : inputs) {
            System.out.printf("%-18s %s%n", s, describe(s));
        }

        // Family-specific factories reject the other family
        System.out.println("Inet4Address.ofLiteral(\"::1\"): " + attempt(() -> Inet4Address.ofLiteral("::1")));
        System.out.println("Inet6Address.ofLiteral(\"10.0.0.7\"): " + attempt(() -> Inet6Address.ofLiteral("10.0.0.7")));

        // A string blocklist misses alternative spellings; a parsed check does not
        for (String s : List.of("127.0.0.1", "127.1", "2130706433")) {
            boolean stringCheck = s.equals("127.0.0.1") || s.equals("localhost");
            boolean parsedCheck = InetAddress.ofLiteral(s).isLoopbackAddress();
            System.out.println(s + ": string blocklist says loopback=" + stringCheck + ", parsed says loopback=" + parsedCheck);
        }
    }

    static String describe(String s) {
        try {
            InetAddress a = InetAddress.ofLiteral(s); // never touches DNS
            String kind = a.isLoopbackAddress() ? "loopback" : a.isSiteLocalAddress() ? "private" : "other";
            return "-> " + a.getClass().getSimpleName() + " " + a.getHostAddress() + " (" + kind + ")";
        } catch (IllegalArgumentException e) {
            return "-> not an IP literal, rejected without a lookup";
        }
    }

    interface Parse {
        InetAddress get();
    }

    static String attempt(Parse p) {
        try {
            return p.get().getHostAddress();
        } catch (IllegalArgumentException e) {
            return e.getClass().getSimpleName();
        }
    }
}

/* Output:
127.0.0.1          -> Inet4Address 127.0.0.1 (loopback)
127.1              -> Inet4Address 127.0.0.1 (loopback)
2130706433         -> Inet4Address 127.0.0.1 (loopback)
10.0.0.7           -> Inet4Address 10.0.0.7 (private)
::1                -> Inet6Address 0:0:0:0:0:0:0:1 (loopback)
[::1]              -> Inet6Address 0:0:0:0:0:0:0:1 (loopback)
::ffff:127.0.0.1   -> Inet4Address 127.0.0.1 (loopback)
256.1.1.1          -> not an IP literal, rejected without a lookup
localhost          -> not an IP literal, rejected without a lookup
internal.example   -> not an IP literal, rejected without a lookup
Inet4Address.ofLiteral("::1"): IllegalArgumentException
Inet6Address.ofLiteral("10.0.0.7"): IllegalArgumentException
127.0.0.1: string blocklist says loopback=true, parsed says loopback=true
127.1: string blocklist says loopback=false, parsed says loopback=true
2130706433: string blocklist says loopback=false, parsed says loopback=true
*/
