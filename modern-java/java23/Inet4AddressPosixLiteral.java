// Inet4Address.ofPosixLiteral (Java 23)
//
// IPv4 literals have more than one spelling, and parsers disagree on them.
// Java's InetAddress.getByName and Inet4Address.ofLiteral read every part
// as decimal, so "0177.0.0.1" is 177.0.0.1. The POSIX inet_addr() function
// used by C libraries, curl and many proxies reads a leading 0 as octal and
// 0x as hexadecimal, so the same string is 127.0.0.1, loopback. When a Java
// service validates a URL host and another component connects to it, that
// difference can let "0177.0.0.1" past a loopback blocklist (an SSRF
// bypass). Java 23 adds Inet4Address.ofPosixLiteral, which parses exactly
// like inet_addr(), so Java can see the address the other side will use.
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.List;

public class Inet4AddressPosixLiteral {
    public static void main(String[] args) {
        List<String> inputs = List.of("127.0.0.1", "0177.0.0.1", "0x7f.1", "017700000001",
                "010.0.0.1", "127.1", "08.0.0.1", "1.2.3.4.5");

        System.out.printf("%-14s %-22s %s%n", "input", "ofLiteral", "ofPosixLiteral");
        for (String s : inputs) {
            System.out.printf("%-14s %-22s %s%n", s, show(s, false), show(s, true));
        }

        // A blocklist check is only as good as its agreement with the connecting parser
        System.out.println();
        for (String host : List.of("0177.0.0.1", "012.0.0.1", "10.0.0.1", "93.184.216.34")) {
            System.out.println(host + ": java-style check allows=" + allowedJavaStyle(host)
                    + ", posix-aware check allows=" + allowedPosixAware(host));
        }
    }

    static String show(String s, boolean posix) {
        try {
            InetAddress a = posix ? Inet4Address.ofPosixLiteral(s) : Inet4Address.ofLiteral(s);
            return a.getHostAddress() + (a.isLoopbackAddress() ? " loopback" : "");
        } catch (IllegalArgumentException e) {
            return "rejected";
        }
    }

    // Naive: validates with Java's parser while the HTTP client may use inet_addr()
    static boolean allowedJavaStyle(String host) {
        try {
            InetAddress a = Inet4Address.ofLiteral(host);
            return !(a.isLoopbackAddress() || a.isSiteLocalAddress());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // Careful: reject the input if the two parsers disagree at all (an ambiguous
    // literal is suspicious), then apply the blocklist to the agreed address
    static boolean allowedPosixAware(String host) {
        InetAddress java, posix;
        try {
            java = Inet4Address.ofLiteral(host);
        } catch (IllegalArgumentException e) {
            java = null;
        }
        try {
            posix = Inet4Address.ofPosixLiteral(host);
        } catch (IllegalArgumentException e) {
            posix = null;
        }
        if (java == null || posix == null || !java.equals(posix)) {
            return false;
        }
        return !(java.isLoopbackAddress() || java.isSiteLocalAddress());
    }
}

/* Output:
input          ofLiteral              ofPosixLiteral
127.0.0.1      127.0.0.1 loopback     127.0.0.1 loopback
0177.0.0.1     177.0.0.1              127.0.0.1 loopback
0x7f.1         rejected               127.0.0.1 loopback
017700000001   rejected               127.0.0.1 loopback
010.0.0.1      10.0.0.1               8.0.0.1
127.1          127.0.0.1 loopback     127.0.0.1 loopback
08.0.0.1       8.0.0.1                rejected
1.2.3.4.5      rejected               rejected

0177.0.0.1: java-style check allows=true, posix-aware check allows=false
012.0.0.1: java-style check allows=true, posix-aware check allows=false
10.0.0.1: java-style check allows=false, posix-aware check allows=false
93.184.216.34: java-style check allows=true, posix-aware check allows=true
*/
