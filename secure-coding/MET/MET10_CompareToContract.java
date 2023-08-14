// MET10-J. Follow the general contract when implementing the compareTo() method
// https://wiki.sei.cmu.edu/confluence/display/java/MET10-J.+Follow+the+general+contract+when+implementing+the+compareTo()+method
//
// compareTo() and Comparator.compare() must be antisymmetric
// (sgn(a.compareTo(b)) == -sgn(b.compareTo(a))), transitive, and consistent.
// Sorting, binary search, TreeMap and TreeSet assume it; when it breaks they
// lose elements, find the wrong ones or throw "Comparison method violates
// its general contract!". A classic way to break it is "return a - b",
// which overflows for operands far apart and flips the sign. Use
// Integer.compare(), Long.compare() or Comparator.comparing...().
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MET10_CompareToContract {
    public static void main(String[] args) {
        PriorityNoncompliant min = new PriorityNoncompliant(Integer.MIN_VALUE);
        PriorityNoncompliant zero = new PriorityNoncompliant(0);
        System.out.println("noncompliant: min.compareTo(zero) = " + min.compareTo(zero)
                + ", zero.compareTo(min) = " + zero.compareTo(min) + " (each is \"less\" than the other)");
        PriorityCompliant cmin = new PriorityCompliant(Integer.MIN_VALUE);
        PriorityCompliant czero = new PriorityCompliant(0);
        System.out.println("compliant:    min.compareTo(zero) = " + cmin.compareTo(czero)
                + ", zero.compareTo(min) = " + czero.compareTo(cmin));

        List<PriorityNoncompliant> bad = new ArrayList<>();
        List<PriorityCompliant> good = new ArrayList<>();
        for (int p : new int[] {5, Integer.MIN_VALUE, Integer.MAX_VALUE, -5}) {
            bad.add(new PriorityNoncompliant(p));
            good.add(new PriorityCompliant(p));
        }
        Collections.sort(bad);
        Collections.sort(good);
        System.out.println("noncompliant sorted: " + bad);
        System.out.println("compliant sorted:    " + good);

        // The job scheduler runs the highest priority first
        System.out.println("noncompliant runs first: " + Collections.max(bad));
        System.out.println("compliant runs first:    " + Collections.max(good));
    }
}

// Noncompliant: subtraction overflows
record PriorityNoncompliant(int value) implements Comparable<PriorityNoncompliant> {
    @Override
    public int compareTo(PriorityNoncompliant o) {
        return value - o.value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}

// Compliant: Integer.compare cannot overflow
record PriorityCompliant(int value) implements Comparable<PriorityCompliant> {
    @Override
    public int compareTo(PriorityCompliant o) {
        return Integer.compare(value, o.value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}

/* Output:
noncompliant: min.compareTo(zero) = -2147483648, zero.compareTo(min) = -2147483648 (each is "less" than the other)
compliant:    min.compareTo(zero) = -1, zero.compareTo(min) = 1
noncompliant sorted: [5, 2147483647, -2147483648, -5]
compliant sorted:    [-2147483648, -5, 5, 2147483647]
noncompliant runs first: -5
compliant runs first:    2147483647
*/
