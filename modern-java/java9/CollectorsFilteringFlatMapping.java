// Java 9 — Collectors.filtering and Collectors.flatMapping (API addition, no JEP)
//
// groupingBy() takes a downstream collector, and Java 8 already had
// mapping() for that position, but not filtering or flat-mapping. Filtering
// the stream BEFORE grouping looks equivalent and is a classic bug: a group
// whose elements are all filtered out disappears from the map instead of
// showing up empty. Flat-mapping each group needed a collect-then-flatten
// second pass. Java 9 added filtering() and flatMapping() as downstream
// collectors, so the filter or flat-map runs inside each group.
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class CollectorsFilteringFlatMapping {
    static final class Employee {
        private final String name;
        private final String team;
        private final int salary;
        private final List<String> skills;

        Employee(String name, String team, int salary, List<String> skills) {
            this.name = name;
            this.team = team;
            this.salary = salary;
            this.skills = skills;
        }

        String name() { return name; }
        String team() { return team; }
        int salary() { return salary; }
        List<String> skills() { return skills; }
    }

    public static void main(String[] args) {
        List<Employee> staff = List.of(
                new Employee("ana", "infra", 5200, List.of("linux", "go")),
                new Employee("bo", "infra", 6100, List.of("k8s", "linux")),
                new Employee("cy", "security", 4800, List.of("java", "crypto")),
                new Employee("di", "security", 4900, List.of("forensics")),
                new Employee("ed", "web", 7000, List.of("java", "react")));

        // Before: filter first, then group -> 'security' vanishes from the result
        Map<String, List<String>> before = staff.stream()
                .filter(e -> e.salary() > 5000)
                .collect(Collectors.groupingBy(Employee::team, TreeMap::new,
                        Collectors.mapping(Employee::name, Collectors.toList())));
        System.out.println("filter then group:  " + before);

        // After: filter inside each group -> every team is present, possibly empty
        Map<String, List<String>> after = staff.stream()
                .collect(Collectors.groupingBy(Employee::team, TreeMap::new,
                        Collectors.filtering(e -> e.salary() > 5000,
                                Collectors.mapping(Employee::name, Collectors.toList()))));
        System.out.println("group then filter:  " + after);

        // Before: collect lists of lists, then flatten in a second pass
        Map<String, List<List<String>>> nested = staff.stream()
                .collect(Collectors.groupingBy(Employee::team, TreeMap::new,
                        Collectors.mapping(Employee::skills, Collectors.toList())));
        Map<String, Set<String>> flattened = new TreeMap<>();
        nested.forEach((team, lists) -> flattened.put(team,
                lists.stream().flatMap(List::stream).collect(Collectors.toCollection(TreeSet::new))));
        System.out.println("two passes:         " + flattened);

        // After: flatMapping does it in the downstream collector
        Map<String, Set<String>> skills = staff.stream()
                .collect(Collectors.groupingBy(Employee::team, TreeMap::new,
                        Collectors.flatMapping(e -> e.skills().stream(),
                                Collectors.toCollection(TreeSet::new))));
        System.out.println("flatMapping:        " + skills);
    }
}

/* Output:
filter then group:  {infra=[ana, bo], web=[ed]}
group then filter:  {infra=[ana, bo], security=[], web=[ed]}
two passes:         {infra=[go, k8s, linux], security=[crypto, forensics, java], web=[java, react]}
flatMapping:        {infra=[go, k8s, linux], security=[crypto, forensics, java], web=[java, react]}
*/
