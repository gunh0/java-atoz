// JEP 395 — Records (Java 16)
//
// A class that is "just data" needed a constructor, accessors, equals,
// hashCode and toString: dozens of lines that hide the few that matter and
// drift out of sync when a field is added. A record declares its state once
// in the header; the compiler derives private final fields, a canonical
// constructor, accessors (name(), not getName()), and value-based equals,
// hashCode and toString. Records are final and shallowly immutable; they may
// add validation (compact constructor), methods, static factories and
// implement interfaces. Local records can be declared inside a method.
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class Records {
    // Before: a hand-written immutable value class
    static final class PointOld {
        private final int x;
        private final int y;

        PointOld(int x, int y) {
            this.x = x;
            this.y = y;
        }

        int x() {
            return x;
        }

        int y() {
            return y;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof PointOld && ((PointOld) o).x == x && ((PointOld) o).y == y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }

        @Override
        public String toString() {
            return "PointOld[x=" + x + ", y=" + y + "]";
        }
    }

    // After: one line, same behavior
    record Point(int x, int y) {
        // Extra methods and static factories are allowed
        static final Point ORIGIN = new Point(0, 0);

        double distanceTo(Point other) {
            return Math.hypot(x - other.x, y - other.y);
        }
    }

    // Compact canonical constructor: validate or normalize, fields are assigned after it
    record Range(int low, int high) {
        Range {
            if (low > high) {
                throw new IllegalArgumentException("low > high: " + low + " > " + high);
            }
        }
    }

    // Defensive copy for a mutable component
    record Team(String name, List<String> members) {
        Team {
            Objects.requireNonNull(name);
            members = List.copyOf(members);
        }
    }

    public static void main(String[] args) {
        Point p = new Point(3, 4);
        System.out.println(p + " vs " + new PointOld(3, 4));
        System.out.println("accessors: x=" + p.x() + ", y=" + p.y());
        System.out.println("equal by value: " + p.equals(new Point(3, 4))
                + ", same hashCode: " + (p.hashCode() == new Point(3, 4).hashCode()));
        System.out.println("distance to origin: " + p.distanceTo(Point.ORIGIN));

        try {
            new Range(10, 1);
        } catch (IllegalArgumentException e) {
            System.out.println("rejected: " + e.getMessage());
        }

        var members = new java.util.ArrayList<>(List.of("ann", "bob"));
        Team team = new Team("core", members);
        members.add("eve"); // does not leak into the record
        System.out.println(team);

        // Local record: a named tuple for an intermediate result
        record Score(String player, int points) {
        }
        List<Score> scores = List.of(new Score("kim", 72), new Score("lee", 95), new Score("park", 88));
        Score best = scores.stream().max(Comparator.comparingInt(Score::points)).orElseThrow();
        System.out.println("best: " + best.player() + " with " + best.points());

        // Records are implicitly final and extend java.lang.Record
        System.out.println("superclass: " + Point.class.getSuperclass().getName()
                + ", is record: " + Point.class.isRecord());
    }
}

/* Output:
Point[x=3, y=4] vs PointOld[x=3, y=4]
accessors: x=3, y=4
equal by value: true, same hashCode: true
distance to origin: 5.0
rejected: low > high: 10 > 1
Team[name=core, members=[ann, bob]]
best: lee with 95
superclass: java.lang.Record, is record: true
*/
