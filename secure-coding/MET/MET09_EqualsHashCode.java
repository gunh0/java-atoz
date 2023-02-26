// MET09-J. Classes that define an equals() method must also define a hashCode() method
// https://wiki.sei.cmu.edu/confluence/display/java/MET09-J.+Classes+that+define+an+equals()+method+must+also+define+a+hashCode()+method
//
// Hash-based collections find an element by hashCode() first and only then
// call equals(). A class that overrides equals() but keeps Object's identity
// hashCode() breaks the contract "equal objects have equal hash codes": two
// equal keys land in different buckets, so a lookup with an equal but
// distinct object misses. For a deny list or a revocation set this means
// the check silently fails open. Override both, using the same fields (or
// use a record, which derives both from its components).
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class MET09_EqualsHashCode {
    public static void main(String[] args) {
        Set<CardNoncompliant> revoked = new HashSet<>();
        revoked.add(new CardNoncompliant("4111-1111"));
        Set<CardCompliant> revokedOk = new HashSet<>();
        revokedOk.add(new CardCompliant("4111-1111"));
        Set<CardRecord> revokedRecord = new HashSet<>();
        revokedRecord.add(new CardRecord("4111-1111"));

        // A later request builds a new object for the same card
        CardNoncompliant presented = new CardNoncompliant("4111-1111");
        System.out.println("equal to the revoked card:    " + presented.equals(new CardNoncompliant("4111-1111")));
        System.out.println("noncompliant, revoked?        " + revoked.contains(presented));
        System.out.println("compliant, revoked?           " + revokedOk.contains(new CardCompliant("4111-1111")));
        System.out.println("record, revoked?              " + revokedRecord.contains(new CardRecord("4111-1111")));
    }
}

// Noncompliant: equals() without hashCode()
final class CardNoncompliant {
    private final String number;

    CardNoncompliant(String number) {
        this.number = number;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CardNoncompliant c && number.equals(c.number);
    }
}

// Compliant: hashCode() uses the same field as equals()
final class CardCompliant {
    private final String number;

    CardCompliant(String number) {
        this.number = number;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CardCompliant c && number.equals(c.number);
    }

    @Override
    public int hashCode() {
        return Objects.hash(number);
    }
}

// Compliant: a record gets consistent equals() and hashCode() for free
record CardRecord(String number) {}

/* Output:
equal to the revoked card:    true
noncompliant, revoked?        false
compliant, revoked?           true
record, revoked?              true
*/
