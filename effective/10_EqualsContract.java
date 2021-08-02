// Effective Java, Item 10: Obey the general contract when overriding equals.
//
// Often the best equals is the one inherited from Object (identity). When a
// class has a notion of logical equality, its equals must be reflexive,
// symmetric (x.equals(y) == y.equals(x)), transitive, consistent, and return
// false for null. Two classic ways to break it:
//  - interoperating with another type in one direction only (symmetry)
//  - adding a value component in a subclass (symmetry or transitivity)
// There is no way to extend an instantiable class with a value component and
// keep the contract; favor composition instead, and remember that
// collections such as List.contains rely on the contract holding.
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class EqualsContract {
    public static void main(String[] args) {
        // 1. Symmetry broken by "helpful" interoperability with String
        BrokenCaseInsensitiveString cis = new BrokenCaseInsensitiveString("Polish");
        String s = "polish";
        System.out.println("cis.equals(s): " + cis.equals(s) + ", s.equals(cis): " + s.equals(cis));
        List<BrokenCaseInsensitiveString> list = new ArrayList<>();
        list.add(cis);
        System.out.println("list.contains(s): " + list.contains(s) + "  <- depends on the implementation");

        // Fixed: only compare with the same type
        System.out.println("fixed equals is symmetric: "
                + (new CaseInsensitiveString("Polish").equals(new CaseInsensitiveString("polish"))
                == new CaseInsensitiveString("polish").equals(new CaseInsensitiveString("Polish"))));

        // 2. Transitivity broken by a subclass that adds a color
        Point p = new Point(1, 2);
        ColorPoint red = new ColorPoint(1, 2, "RED");
        ColorPoint blue = new ColorPoint(1, 2, "BLUE");
        System.out.println("red == p: " + red.equals(p) + ", p == blue: " + p.equals(blue)
                + ", red == blue: " + red.equals(blue) + "  <- not transitive");

        // Fixed: composition. A ComposedColorPoint HAS a Point, and offers a view
        ComposedColorPoint redC = new ComposedColorPoint(1, 2, "RED");
        ComposedColorPoint blueC = new ComposedColorPoint(1, 2, "BLUE");
        System.out.println("composed: red == blue: " + redC.equals(blueC)
                + ", red == p: " + redC.equals(p)
                + ", red.asPoint() == p: " + redC.asPoint().equals(p));

        // The other properties
        System.out.println("reflexive: " + p.equals(p) + ", null: " + p.equals(null));
    }
}

// Broken: tries to be equal to plain Strings, but String does not know about it
final class BrokenCaseInsensitiveString {
    private final String s;

    BrokenCaseInsensitiveString(String s) {
        this.s = Objects.requireNonNull(s);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof BrokenCaseInsensitiveString) {
            return s.equalsIgnoreCase(((BrokenCaseInsensitiveString) o).s);
        }
        if (o instanceof String) { // one-way interoperability!
            return s.equalsIgnoreCase((String) o);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return s.toLowerCase().hashCode();
    }
}

final class CaseInsensitiveString {
    private final String s;

    CaseInsensitiveString(String s) {
        this.s = Objects.requireNonNull(s);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CaseInsensitiveString
                && ((CaseInsensitiveString) o).s.equalsIgnoreCase(s);
    }

    @Override
    public int hashCode() {
        return s.toLowerCase().hashCode();
    }
}

class Point {
    private final int x, y;

    Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;                     // reflexive, and a cheap shortcut
        }
        if (!(o instanceof Point)) {
            return false;                    // also handles null
        }
        Point p = (Point) o;
        return p.x == x && p.y == y;         // compare the significant fields
    }

    @Override
    public int hashCode() {
        return 31 * Integer.hashCode(x) + Integer.hashCode(y);
    }
}

// Broken: mixed comparison ignores color to restore symmetry, losing transitivity
class ColorPoint extends Point {
    private final String color;

    ColorPoint(int x, int y, String color) {
        super(x, y);
        this.color = color;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Point)) {
            return false;
        }
        if (!(o instanceof ColorPoint)) {
            return o.equals(this);           // plain Point: color-blind comparison
        }
        return super.equals(o) && ((ColorPoint) o).color.equals(color);
    }
}

// Recommended: composition instead of inheritance
final class ComposedColorPoint {
    private final Point point;
    private final String color;

    ComposedColorPoint(int x, int y, String color) {
        this.point = new Point(x, y);
        this.color = Objects.requireNonNull(color);
    }

    Point asPoint() {
        return point;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ComposedColorPoint)) {
            return false;
        }
        ComposedColorPoint cp = (ComposedColorPoint) o;
        return cp.point.equals(point) && cp.color.equals(color);
    }

    @Override
    public int hashCode() {
        return 31 * point.hashCode() + color.hashCode();
    }
}

/* Output:
cis.equals(s): true, s.equals(cis): false
list.contains(s): false  <- depends on the implementation
fixed equals is symmetric: true
red == p: true, p == blue: true, red == blue: false  <- not transitive
composed: red == blue: false, red == p: false, red.asPoint() == p: true
reflexive: true, null: false
*/
