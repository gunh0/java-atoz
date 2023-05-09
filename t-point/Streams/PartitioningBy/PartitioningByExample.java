// Collectors.partitioningBy(predicate) is groupingBy with a boolean key: it
// always returns a Map<Boolean, ...> with BOTH keys, true and false, even
// when one side is empty (groupingBy(p) would simply omit the missing key).
// That makes it the natural "split into pass/fail" collector. Like groupingBy
// it takes a downstream collector: counting(), mapping(..), and so on.
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PartitioningByExample {
    record Dependency(String name, String version, boolean hasKnownVuln) {}

    public static void main(String[] args) {
        List<Dependency> deps = List.of(
                new Dependency("log-lib", "2.14.1", true),
                new Dependency("json-lib", "2.15.0", false),
                new Dependency("http-client", "4.5.13", false),
                new Dependency("xml-parser", "1.2", true),
                new Dependency("yaml-lib", "1.33", false));

        Map<Boolean, List<String>> split = deps.stream()
                .collect(Collectors.partitioningBy(Dependency::hasKnownVuln,
                        Collectors.mapping(d -> d.name() + ":" + d.version(), Collectors.toList())));
        System.out.println("vulnerable: " + split.get(true));
        System.out.println("clean:      " + split.get(false));

        Map<Boolean, Long> counts = deps.stream()
                .collect(Collectors.partitioningBy(Dependency::hasKnownVuln, Collectors.counting()));
        System.out.println("counts: " + counts);

        // Both keys are always there
        List<Integer> ports = List.of(22, 80, 443);
        Map<Boolean, List<Integer>> highPorts = ports.stream()
                .collect(Collectors.partitioningBy(p -> p > 1024));
        System.out.println("partitioningBy(> 1024): " + highPorts);
        Map<Boolean, List<Integer>> grouped = ports.stream()
                .collect(Collectors.groupingBy(p -> p > 1024));
        System.out.println("groupingBy(> 1024):     " + grouped + " (no 'true' key)");

        // A practical use: validate input and report all bad lines at once
        List<String> cidrs = List.of("10.0.0.0/8", "192.168.1.0/33", "172.16.0.0/12", "not-an-ip");
        Map<Boolean, List<String>> valid = cidrs.stream()
                .collect(Collectors.partitioningBy(PartitioningByExample::isValidCidr));
        if (!valid.get(false).isEmpty()) {
            System.out.println("rejected " + valid.get(false).size() + " entries: " + valid.get(false));
        }
        System.out.println("accepted: " + valid.get(true));
    }

    static boolean isValidCidr(String s) {
        String[] parts = s.split("/");
        if (parts.length != 2 || !parts[1].matches("\\d{1,2}") || Integer.parseInt(parts[1]) > 32) return false;
        String[] octets = parts[0].split("\\.");
        if (octets.length != 4) return false;
        for (String o : octets) {
            if (!o.matches("\\d{1,3}") || Integer.parseInt(o) > 255) return false;
        }
        return true;
    }
}

/* Output:
vulnerable: [log-lib:2.14.1, xml-parser:1.2]
clean:      [json-lib:2.15.0, http-client:4.5.13, yaml-lib:1.33]
counts: {false=3, true=2}
partitioningBy(> 1024): {false=[22, 80, 443], true=[]}
groupingBy(> 1024):     {false=[22, 80, 443]} (no 'true' key)
rejected 2 entries: [192.168.1.0/33, not-an-ip]
accepted: [10.0.0.0/8, 172.16.0.0/12]
*/
