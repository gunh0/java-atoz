// Comparable<T> gives a class its NATURAL ORDERING through compareTo(), which
// returns a negative number, zero or a positive number. Collections.sort,
// TreeSet, TreeMap and Arrays.sort use it when no Comparator is supplied.
//  - compare fields with Integer.compare / Long.compare, never by subtraction:
//    a - b overflows for large values of opposite sign and flips the result
//  - keep compareTo consistent with equals (compareTo == 0 iff equals): a
//    TreeSet uses compareTo alone and silently drops "equal" elements
//    (BigDecimal is the famous inconsistent case: 1.0 vs 1.00)
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ComparableExample {
    public static void main(String[] args) {
        List<Version> versions = new ArrayList<>(List.of(
                new Version(1, 10, 0), new Version(1, 2, 3), new Version(0, 9, 9), new Version(1, 2, 0)));
        Collections.sort(versions); // uses Version.compareTo
        System.out.println("sorted versions: " + versions);
        System.out.println("max: " + Collections.max(versions));
        System.out.println("1.2.3 vs 1.10.0: " + Integer.signum(new Version(1, 2, 3).compareTo(new Version(1, 10, 0))));

        // Built-in natural orderings: String is lexicographic by char code
        List<String> files = new ArrayList<>(List.of("file10", "File2", "file2"));
        Collections.sort(files);
        System.out.println("String natural order: " + files);

        // Subtraction overflows; Integer.compare does not
        int big = Integer.MAX_VALUE, negative = -1;
        System.out.println("subtraction says MAX_VALUE < -1: " + (big - negative < 0));
        System.out.println("Integer.compare(MAX_VALUE, -1): " + Integer.compare(big, negative));

        // Inconsistent with equals: BigDecimal 1.0 and 1.00
        BigDecimal a = new BigDecimal("1.0"), b = new BigDecimal("1.00");
        System.out.println("equals: " + a.equals(b) + ", compareTo: " + a.compareTo(b));
        Set<BigDecimal> hash = new HashSet<>(List.of(a, b));
        Set<BigDecimal> tree = new TreeSet<>(List.of(a, b));
        System.out.println("HashSet size " + hash.size() + ", TreeSet size " + tree.size());
    }
}

// A semantic version: major, then minor, then patch
final class Version implements Comparable<Version> {
    private final int major, minor, patch;

    Version(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    @Override
    public int compareTo(Version other) {
        int c = Integer.compare(major, other.major);
        if (c != 0) return c;
        c = Integer.compare(minor, other.minor);
        if (c != 0) return c;
        return Integer.compare(patch, other.patch);
    }

    // Consistent with compareTo: same three fields
    @Override
    public boolean equals(Object o) {
        return o instanceof Version v && compareTo(v) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * major + minor) + patch;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}

/* Output:
sorted versions: [0.9.9, 1.2.0, 1.2.3, 1.10.0]
max: 1.10.0
1.2.3 vs 1.10.0: -1
String natural order: [File2, file10, file2]
subtraction says MAX_VALUE < -1: true
Integer.compare(MAX_VALUE, -1): 1
equals: false, compareTo: 0
HashSet size 2, TreeSet size 1
*/
