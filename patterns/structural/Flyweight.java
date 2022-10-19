// Flyweight (structural): use sharing to support large numbers of
// fine-grained objects efficiently.
//
// Split an object's state into INTRINSIC state, which is the same for many
// objects and can be shared, and EXTRINSIC state, which differs per use and
// is passed in by the caller. A factory hands out one shared, immutable
// instance per intrinsic value. Here a firewall log of thousands of
// connection records shares one Protocol/Service object per (protocol, port).
// JDK examples: Integer.valueOf (and Long, Short, Byte, Character) caches the
// boxed values -128..127, and Boolean.valueOf returns TRUE/FALSE. That cache
// is also why == on boxed numbers "works" for small values only.
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FlyweightDemo {
    // The flyweight: immutable, intrinsic state only
    record Service(String protocol, int port, String name, String risk) {}

    // The flyweight factory
    static final class ServiceCatalog {
        private final Map<String, Service> cache = new ConcurrentHashMap<>();
        private int created;

        Service get(String protocol, int port) {
            return cache.computeIfAbsent(protocol + "/" + port, key -> {
                created++;
                return new Service(protocol, port, nameOf(port), port == 23 || port == 3389 ? "high" : "normal");
            });
        }

        int created() { return created; }

        private static String nameOf(int port) {
            return switch (port) {
                case 22 -> "ssh";
                case 23 -> "telnet";
                case 53 -> "dns";
                case 443 -> "https";
                case 3389 -> "rdp";
                default -> "port-" + port;
            };
        }
    }

    // The context object: extrinsic state + a reference to the shared flyweight
    record Connection(int sourceIp, Service service, boolean allowed) {}

    public static void main(String[] args) {
        ServiceCatalog catalog = new ServiceCatalog();
        int[] ports = {443, 443, 53, 22, 443, 3389, 53, 23};
        List<Connection> log = new ArrayList<>();

        // 10,000 connection records, generated deterministically
        for (int i = 0; i < 10_000; i++) {
            int port = ports[i % ports.length];
            String protocol = port == 53 ? "udp" : "tcp";
            log.add(new Connection(0x0A000000 + i, catalog.get(protocol, port), port != 23));
        }

        Set<Service> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
        log.forEach(c -> distinct.add(c.service()));
        System.out.println("connections: " + log.size());
        System.out.println("Service objects created: " + catalog.created()
                + ", distinct instances referenced: " + distinct.size());
        long risky = log.stream().filter(c -> c.service().risk().equals("high")).count();
        System.out.println("connections to high-risk services: " + risky);
        System.out.println("same flyweight for tcp/443: " + (catalog.get("tcp", 443) == log.get(0).service()));

        // The JDK's flyweight factory for boxed integers
        Integer a = Integer.valueOf(127), b = Integer.valueOf(127);
        Integer c = Integer.valueOf(128), d = Integer.valueOf(128);
        System.out.println("Integer.valueOf(127) shared: " + (a == b));
        System.out.println("Integer.valueOf(128) shared: " + (c == d) + " (outside the default cache)");
        System.out.println("so compare boxed values with equals(): " + c.equals(d));
    }
}

/* Output:
connections: 10000
Service objects created: 5, distinct instances referenced: 5
connections to high-risk services: 2500
same flyweight for tcp/443: true
Integer.valueOf(127) shared: true
Integer.valueOf(128) shared: false (outside the default cache)
so compare boxed values with equals(): true
*/
