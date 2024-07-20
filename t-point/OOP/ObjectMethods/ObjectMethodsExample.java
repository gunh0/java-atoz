// Every class extends java.lang.Object and inherits its methods:
//  - toString()   default is ClassName@hexHash, override it for logs/debugging
//  - equals(o)    default is identity (==); override for value semantics
//  - hashCode()   must agree with equals: equal objects, equal hash codes
//  - getClass()   the runtime class (final, cannot be overridden)
//  - clone()      protected, shallow field copy, requires Cloneable
//  - wait/notify/notifyAll for monitors; finalize() is deprecated for removal
// java.util.Objects offers null-safe helpers: equals, hash, toString,
// requireNonNull, requireNonNullElse.
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ObjectMethodsExample {

    // Relies on Object's defaults
    static class PlainPoint {
        final int x, y;

        PlainPoint(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    // Overrides the value-related methods
    static class Point implements Cloneable {
        final int x, y;
        List<String> tags = new ArrayList<>();

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            // getClass() comparison: a subclass instance is never equal to a Point
            if (o == null || getClass() != o.getClass()) return false;
            Point p = (Point) o;
            return x == p.x && y == p.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }

        @Override
        public String toString() {
            return "Point(" + x + ", " + y + ", tags=" + tags + ")";
        }

        @Override
        public Point clone() {
            try {
                return (Point) super.clone(); // shallow: the tags list is shared
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
    }

    static class NotCloneable {
        Object tryClone() throws CloneNotSupportedException {
            return super.clone();
        }
    }

    public static void main(String[] args) throws Exception {
        PlainPoint a = new PlainPoint(1, 2);
        PlainPoint b = new PlainPoint(1, 2);
        System.out.println("default toString looks like Class@hash: "
                + a.toString().matches(".*PlainPoint@[0-9a-f]+"));
        System.out.println("default equals is identity: " + a.equals(b) + ", " + a.equals(a));

        Point p = new Point(1, 2);
        Point q = new Point(1, 2);
        System.out.println("p = " + p);
        System.out.println("p.equals(q): " + p.equals(q) + ", same hashCode: " + (p.hashCode() == q.hashCode()));
        System.out.println("p.equals(null): " + p.equals(null));
        System.out.println("getClass(): " + p.getClass().getSimpleName()
                + ", superclass: " + p.getClass().getSuperclass().getSimpleName());

        // clone() copies fields: primitives are independent, references are shared
        Point copy = p.clone();
        copy.tags.add("shared!");
        System.out.println("copy == p: " + (copy == p) + ", copy.equals(p): " + copy.equals(p));
        System.out.println("original after modifying the clone's list: " + p);

        try {
            new NotCloneable().tryClone();
        } catch (CloneNotSupportedException e) {
            System.out.println("clone without Cloneable -> " + e.getClass().getSimpleName());
        }

        // java.util.Objects helpers
        System.out.println("Objects.equals(null, null): " + Objects.equals(null, null));
        System.out.println("Objects.toString(null, \"n/a\"): " + Objects.toString(null, "n/a"));
        System.out.println("Objects.hash(1, 2) == Arrays-style hash: " + (Objects.hash(1, 2) == 31 * (31 + 1) + 2));
        System.out.println("requireNonNullElse: " + Objects.requireNonNullElse(null, "default"));
        try {
            Objects.requireNonNull(null, "name must not be null");
        } catch (NullPointerException e) {
            System.out.println("requireNonNull -> NullPointerException: " + e.getMessage());
        }
    }
}

/* Output:
default toString looks like Class@hash: true
default equals is identity: false, true
p = Point(1, 2, tags=[])
p.equals(q): true, same hashCode: true
p.equals(null): false
getClass(): Point, superclass: Object
copy == p: false, copy.equals(p): true
original after modifying the clone's list: Point(1, 2, tags=[shared!])
clone without Cloneable -> CloneNotSupportedException
Objects.equals(null, null): true
Objects.toString(null, "n/a"): n/a
Objects.hash(1, 2) == Arrays-style hash: true
requireNonNullElse: default
requireNonNull -> NullPointerException: name must not be null
*/
