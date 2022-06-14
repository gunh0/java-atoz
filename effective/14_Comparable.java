// Effective Java, Item 14: Consider implementing Comparable.
//
// A class with a natural ordering (numbers, dates, names, versions) should
// implement Comparable, so it works out of the box with sorting, TreeSet,
// TreeMap, binary search, Collections.max and friends. compareTo must be
// antisymmetric, transitive and should be consistent with equals; when it is
// not (BigDecimal is the famous case), sorted and hashed collections disagree
// about what counts as a duplicate. Compare fields from most to least
// significant with Integer.compare or the Comparator construction methods,
// never with subtraction, which overflows.
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ComparableDemo {
    public static void main(String[] args) {
        List<Version> versions = new ArrayList<>(List.of(
                Version.parse("1.10.0"), Version.parse("1.2.3"), Version.parse("1.2.10"),
                Version.parse("0.9.9"), Version.parse("1.2.3")));

        // Natural ordering makes the whole collections framework available
        System.out.println("TreeSet:      " + new TreeSet<>(versions));
        versions.sort(null);
        System.out.println("sorted list:  " + versions);
        System.out.println("max:          " + versions.stream().max(Comparator.naturalOrder()).orElseThrow());

        // Sorting the raw strings gets it wrong: "1.10.0" < "1.2.3" lexicographically
        System.out.println("as strings:   " + new TreeSet<>(List.of("1.10.0", "1.2.3", "1.2.10", "0.9.9")));

        // Subtraction-based comparator: overflow flips the sign
        Comparator<Integer> bySubtraction = (a, b) -> a - b;
        Comparator<Integer> byCompare = Integer::compare;
        int big = Integer.MAX_VALUE, negative = -1;
        System.out.println("subtraction says MAX_VALUE < -1: " + (bySubtraction.compare(big, negative) < 0));
        System.out.println("Integer.compare says MAX_VALUE < -1: " + (byCompare.compare(big, negative) < 0));

        // compareTo inconsistent with equals: BigDecimal("1.0") vs BigDecimal("1.00")
        Set<BigDecimal> hashed = new HashSet<>(List.of(new BigDecimal("1.0"), new BigDecimal("1.00")));
        Set<BigDecimal> sorted = new TreeSet<>(List.of(new BigDecimal("1.0"), new BigDecimal("1.00")));
        System.out.println("BigDecimal HashSet size (equals):    " + hashed.size());
        System.out.println("BigDecimal TreeSet size (compareTo): " + sorted.size());
    }
}

final class Version implements Comparable<Version> {
    // Built once: compare major, then minor, then patch
    private static final Comparator<Version> ORDER =
            Comparator.comparingInt((Version v) -> v.major)
                    .thenComparingInt(v -> v.minor)
                    .thenComparingInt(v -> v.patch);

    private final int major, minor, patch;

    private Version(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    static Version parse(String s) {
        String[] parts = s.split("\\.");
        return new Version(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    @Override
    public int compareTo(Version other) {
        return ORDER.compare(this, other);
    }

    // Consistent with compareTo: equal exactly when compareTo returns 0
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
TreeSet:      [0.9.9, 1.2.3, 1.2.10, 1.10.0]
sorted list:  [0.9.9, 1.2.3, 1.2.3, 1.2.10, 1.10.0]
max:          1.10.0
as strings:   [0.9.9, 1.10.0, 1.2.10, 1.2.3]
subtraction says MAX_VALUE < -1: true
Integer.compare says MAX_VALUE < -1: false
BigDecimal HashSet size (equals):    2
BigDecimal TreeSet size (compareTo): 1
*/
