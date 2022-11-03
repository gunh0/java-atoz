// A Comparator<T> is an ordering that lives OUTSIDE the class. Use it when a
// class has no natural ordering, when you need several orderings, or when you
// cannot edit the class. Since Java 8 comparators are built by composition:
//   Comparator.comparing(keyExtractor)      order by a derived key
//   .thenComparing(...)                     tie-breaker
//   .reversed()                             flip the whole chain built so far
//   Comparator.nullsFirst/nullsLast(...)    decide where nulls go
//   comparingInt/Long/Double                avoid boxing the key
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ComparatorExample {
    public static void main(String[] args) {
        List<Employee> staff = List.of(
                new Employee("Kim", "dev", 5200),
                new Employee("Lee", "ops", 4100),
                new Employee("Park", "dev", 6100),
                new Employee("Choi", "ops", 4100),
                new Employee("Jung", null, 3900));

        print("by name", staff, Comparator.comparing(Employee::name));
        print("by salary", staff, Comparator.comparingInt(Employee::salary));
        print("by salary desc", staff, Comparator.comparingInt(Employee::salary).reversed());

        // Tie-breaker: Lee and Choi earn the same, so name decides
        print("by salary, then name", staff,
                Comparator.comparingInt(Employee::salary).thenComparing(Employee::name));

        // Department may be null: comparing(Employee::team) alone would throw NPE
        print("by team (nulls first), then salary desc", staff,
                Comparator.comparing(Employee::team, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(Employee::salary, Comparator.reverseOrder()));

        // Careful: reversed() applies to EVERYTHING chained before it
        print("(team, name).reversed()", staff.subList(0, 4),
                Comparator.comparing(Employee::team).thenComparing(Employee::name).reversed());

        // Comparators also feed min/max and sorted collections
        Employee top = staff.stream().max(Comparator.comparingInt(Employee::salary)).orElseThrow();
        System.out.println("highest paid: " + top.name());

        // The old way: an anonymous class, still a valid Comparator
        Comparator<Employee> byNameLength = new Comparator<>() {
            @Override
            public int compare(Employee a, Employee b) {
                return Integer.compare(a.name().length(), b.name().length());
            }
        };
        print("by name length (stable sort keeps input order on ties)", staff, byNameLength);
    }

    private static void print(String label, List<Employee> input, Comparator<Employee> order) {
        List<Employee> copy = new ArrayList<>(input);
        copy.sort(order);
        System.out.println(label + ": " + copy);
    }
}

record Employee(String name, String team, int salary) {
    @Override
    public String toString() {
        return name + "(" + team + "," + salary + ")";
    }
}

/* Output:
by name: [Choi(ops,4100), Jung(null,3900), Kim(dev,5200), Lee(ops,4100), Park(dev,6100)]
by salary: [Jung(null,3900), Lee(ops,4100), Choi(ops,4100), Kim(dev,5200), Park(dev,6100)]
by salary desc: [Park(dev,6100), Kim(dev,5200), Lee(ops,4100), Choi(ops,4100), Jung(null,3900)]
by salary, then name: [Jung(null,3900), Choi(ops,4100), Lee(ops,4100), Kim(dev,5200), Park(dev,6100)]
by team (nulls first), then salary desc: [Jung(null,3900), Park(dev,6100), Kim(dev,5200), Lee(ops,4100), Choi(ops,4100)]
(team, name).reversed(): [Lee(ops,4100), Choi(ops,4100), Park(dev,6100), Kim(dev,5200)]
highest paid: Park
by name length (stable sort keeps input order on ties): [Kim(dev,5200), Lee(ops,4100), Park(dev,6100), Choi(ops,4100), Jung(null,3900)]
*/
