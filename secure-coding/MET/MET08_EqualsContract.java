// MET08-J. Preserve the equality contract when overriding the equals() method
// https://wiki.sei.cmu.edu/confluence/display/java/MET08-J.+Preserve+the+equality+contract+when+overriding+the+equals()+method
//
// equals() must be reflexive, symmetric, transitive and consistent. A
// common way to break symmetry is to make a class "interoperate" with
// another type: a case-insensitive name that equals a plain String, while
// String.equals() knows nothing about it. Whether a check succeeds then
// depends on which side of the comparison an object ends up on, for
// instance on whether a collection calls a.equals(b) or b.equals(a). A
// blocklist can be bypassed this way. Compare only with instances of the
// same type, and expose explicit methods for cross-type comparisons.
import java.util.List;
import java.util.Locale;

public class MET08_EqualsContract {
    public static void main(String[] args) {
        NameNoncompliant blocked = new NameNoncompliant("Admin");
        String requested = "admin";
        System.out.println("blocked.equals(requested): " + blocked.equals(requested));
        System.out.println("requested.equals(blocked): " + requested.equals(blocked));

        // The same blocklist, checked two ways
        List<Object> blocklist = List.of(blocked);
        System.out.println("noncompliant, blocklist.contains(\"admin\"): " + blocklist.contains(requested));
        System.out.println("noncompliant, blocked matches \"admin\":    " + blocked.equals(requested));

        List<NameCompliant> fixed = List.of(new NameCompliant("Admin"));
        System.out.println("compliant,    blocklist.contains(\"admin\"): " + fixed.contains(new NameCompliant(requested)));
        System.out.println("compliant,    symmetric: " + new NameCompliant(requested).equals(fixed.get(0))
                + " / " + fixed.get(0).equals(new NameCompliant(requested)));
    }
}

// Noncompliant: equal to Strings, but Strings are never equal to it
final class NameNoncompliant {
    private final String s;

    NameNoncompliant(String s) {
        this.s = s;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof NameNoncompliant n) {
            return s.equalsIgnoreCase(n.s);
        }
        if (o instanceof String str) { // one-way interoperability
            return s.equalsIgnoreCase(str);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return s.toLowerCase(Locale.ROOT).hashCode();
    }
}

// Compliant: only equal to other NameCompliant instances
final class NameCompliant {
    private final String s;

    NameCompliant(String s) {
        this.s = s;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NameCompliant n && s.equalsIgnoreCase(n.s);
    }

    @Override
    public int hashCode() {
        return s.toLowerCase(Locale.ROOT).hashCode();
    }
}

/* Output:
blocked.equals(requested): true
requested.equals(blocked): false
noncompliant, blocklist.contains("admin"): false
noncompliant, blocked matches "admin":    true
compliant,    blocklist.contains("admin"): true
compliant,    symmetric: true / true
*/
