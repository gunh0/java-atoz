// HashSet stores unique elements in a hash table (it is a HashMap underneath,
// with the elements as keys). Uniqueness is decided by hashCode() + equals():
//  - a class that overrides equals() but not hashCode() lets "equal" objects
//    land in different buckets, so the set keeps duplicates
//  - mutating a field used by hashCode() after insertion strands the element in
//    the wrong bucket: contains() and remove() can no longer find it
//  - iteration order is unspecified; never rely on it (sort, or use
//    LinkedHashSet / TreeSet when order matters)
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class HashSetExample {
    public static void main(String[] args) {
        // add() returns false for a duplicate
        Set<String> visitors = new HashSet<>();
        System.out.println("add alice: " + visitors.add("alice"));
        System.out.println("add bob:   " + visitors.add("bob"));
        System.out.println("add alice: " + visitors.add("alice"));
        System.out.println("unique visitors: " + visitors.size());

        // Deduplicate a list, then sort for stable output
        List<String> ips = List.of("10.0.0.2", "10.0.0.1", "10.0.0.2", "10.0.0.3", "10.0.0.1");
        Set<String> uniqueIps = new HashSet<>(ips);
        System.out.println("unique IPs (sorted): " + new TreeSet<>(uniqueIps));

        // Set algebra with retainAll / removeAll
        Set<String> admins = new HashSet<>(Set.of("alice", "carol"));
        Set<String> online = new HashSet<>(Set.of("alice", "bob"));
        Set<String> onlineAdmins = new HashSet<>(admins);
        onlineAdmins.retainAll(online);
        System.out.println("online admins: " + onlineAdmins);

        // equals() without hashCode(): duplicates slip in
        Set<BrokenPoint> broken = new HashSet<>();
        broken.add(new BrokenPoint(1, 2));
        broken.add(new BrokenPoint(1, 2));
        System.out.println("BrokenPoint set size: " + broken.size() + " (expected 1)");

        Set<Point> points = new HashSet<>();
        points.add(new Point(1, 2));
        points.add(new Point(1, 2));
        System.out.println("Point set size: " + points.size());

        // Mutating an element after insertion
        Point p = new Point(3, 4);
        points.add(p);
        p.x = 99; // hashCode changes, but the element stays in its old bucket
        System.out.println("contains(p) after mutation: " + points.contains(p));
        System.out.println("remove(p) after mutation:   " + points.remove(p));
        System.out.println("size still: " + points.size());
    }
}

// equals is overridden but hashCode is inherited from Object
class BrokenPoint {
    final int x, y;

    BrokenPoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BrokenPoint b && b.x == x && b.y == y;
    }
}

// Correct pair, but the fields are mutable to show the stranding problem
class Point {
    int x, y;

    Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Point q && q.x == x && q.y == y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}

/* Output:
add alice: true
add bob:   true
add alice: false
unique visitors: 2
unique IPs (sorted): [10.0.0.1, 10.0.0.2, 10.0.0.3]
online admins: [alice]
BrokenPoint set size: 2 (expected 1)
Point set size: 1
contains(p) after mutation: false
remove(p) after mutation:   false
size still: 2
*/
