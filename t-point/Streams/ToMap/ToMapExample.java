// Collectors.toMap(keyMapper, valueMapper) builds a Map from a stream. Watch:
//  - a duplicate key throws IllegalStateException ("Duplicate key ...");
//    pass a merge function (old, new) -> ... to decide which value wins
//  - a null VALUE throws NullPointerException (HashMap.merge is used inside)
//  - the map type is unspecified; the four-argument form takes a supplier
//    such as TreeMap::new or LinkedHashMap::new
// Use Function.identity() when the element itself is the key or value, and
// toUnmodifiableMap for an immutable result.
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ToMapExample {
    record Host(String name, String ip, int openPorts) {}

    public static void main(String[] args) {
        List<Host> inventory = List.of(
                new Host("web01", "10.0.0.11", 2),
                new Host("db01", "10.0.0.21", 1),
                new Host("web02", "10.0.0.12", 3));

        // name -> ip, sorted by name
        Map<String, String> ipByName = inventory.stream()
                .collect(Collectors.toMap(Host::name, Host::ip, (a, b) -> a, TreeMap::new));
        System.out.println("ip by name: " + ipByName);

        // An index of the objects themselves
        Map<String, Host> byIp = inventory.stream()
                .collect(Collectors.toMap(Host::ip, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        System.out.println("10.0.0.21 -> " + byIp.get("10.0.0.21").name());

        // Duplicate keys: the scanner reported 10.0.0.11 twice
        List<Host> scan = List.of(
                new Host("web01", "10.0.0.11", 2),
                new Host("web01-old", "10.0.0.11", 5),
                new Host("db01", "10.0.0.21", 1));
        try {
            scan.stream().collect(Collectors.toMap(Host::ip, Host::name));
        } catch (IllegalStateException e) {
            System.out.println("IllegalStateException: " + e.getMessage());
        }

        // Merge functions decide what a collision means
        Map<String, String> keepFirst = scan.stream()
                .collect(Collectors.toMap(Host::ip, Host::name, (first, second) -> first, TreeMap::new));
        Map<String, String> keepAll = scan.stream()
                .collect(Collectors.toMap(Host::ip, Host::name, (a, b) -> a + "+" + b, TreeMap::new));
        Map<String, Integer> sumPorts = scan.stream()
                .collect(Collectors.toMap(Host::ip, Host::openPorts, Integer::sum, TreeMap::new));
        System.out.println("keep first: " + keepFirst);
        System.out.println("keep all:   " + keepAll);
        System.out.println("sum ports:  " + sumPorts);

        // Null values are rejected
        try {
            List.of("alice", "bob").stream()
                    .collect(Collectors.toMap(Function.identity(), u -> u.equals("bob") ? null : "admin"));
        } catch (NullPointerException e) {
            System.out.println("null value -> NullPointerException");
        }

        // Immutable result
        Map<String, Integer> ports = inventory.stream()
                .collect(Collectors.toUnmodifiableMap(Host::name, Host::openPorts));
        try {
            ports.put("new", 0);
        } catch (UnsupportedOperationException e) {
            System.out.println("toUnmodifiableMap().put -> UnsupportedOperationException");
        }
    }
}

/* Output:
ip by name: {db01=10.0.0.21, web01=10.0.0.11, web02=10.0.0.12}
10.0.0.21 -> db01
IllegalStateException: Duplicate key 10.0.0.11 (attempted merging values web01 and web01-old)
keep first: {10.0.0.11=web01, 10.0.0.21=db01}
keep all:   {10.0.0.11=web01+web01-old, 10.0.0.21=db01}
sum ports:  {10.0.0.11=7, 10.0.0.21=1}
null value -> NullPointerException
toUnmodifiableMap().put -> UnsupportedOperationException
*/
