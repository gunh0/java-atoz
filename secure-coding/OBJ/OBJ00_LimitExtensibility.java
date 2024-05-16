// OBJ00-J. Limit extensibility of classes and methods with invariants to trusted subclasses only
// https://wiki.sei.cmu.edu/confluence/display/java/OBJ00-J.+Limit+extensibility+of+classes+and+methods+with+invariants+to+trusted+subclasses+only
//
// If a class is not final, anyone can subclass it and override its
// methods, so an instance of "your" type may run attacker code. A value
// that was checked can change after the check, because the subclass
// decides what each call returns. java.math.BigInteger is the textbook
// case: it is not final, so a BigInteger argument may be a hostile
// subclass. Make classes with invariants final (or give them private
// constructors, or seal them), and when you accept an instance of a
// non-final type from untrusted code, copy it into the trusted class
// before validating and using it.
import java.math.BigInteger;

public class OBJ00_LimitExtensibility {
    static final BigInteger LIMIT = BigInteger.valueOf(1_000);

    public static void main(String[] args) {
        BigInteger hostile = new FlipFlopInteger();

        // Noncompliant: validates, then uses, the caller's object
        System.out.println("noncompliant: " + transferNoncompliant(hostile));

        // Compliant: normalize into a genuine BigInteger first
        System.out.println("compliant:    " + transferCompliant(hostile));

        // Final classes cannot be subclassed at all
        System.out.println("Amount is final: " + java.lang.reflect.Modifier.isFinal(Amount.class.getModifiers()));
        System.out.println("Amount.of(500) = " + Amount.of(BigInteger.valueOf(500)).value());
    }

    static String transferNoncompliant(BigInteger amount) {
        if (amount.compareTo(LIMIT) > 0) {
            return "rejected";
        }
        return "transferred " + amount.toString();
    }

    static String transferCompliant(BigInteger amount) {
        BigInteger copy = amount.getClass() == BigInteger.class ? amount : new BigInteger(amount.toByteArray());
        if (copy.compareTo(LIMIT) > 0) {
            return "rejected " + copy;
        }
        return "transferred " + copy;
    }
}

// An attacker's subclass: small when checked, huge when used
class FlipFlopInteger extends BigInteger {
    private boolean checked;

    FlipFlopInteger() {
        super("1000000"); // the real value
    }

    @Override
    public int compareTo(BigInteger val) {
        if (!checked) {
            checked = true;
            return -1; // "less than the limit"
        }
        return super.compareTo(val);
    }
}

// Compliant design: a final value class validates once and cannot be subverted
final class Amount {
    private final BigInteger value;

    private Amount(BigInteger value) {
        this.value = value;
    }

    static Amount of(BigInteger raw) {
        BigInteger copy = new BigInteger(raw.toByteArray());
        if (copy.signum() < 0 || copy.compareTo(OBJ00_LimitExtensibility.LIMIT) > 0) {
            throw new IllegalArgumentException("amount out of range");
        }
        return new Amount(copy);
    }

    BigInteger value() {
        return value;
    }
}

/* Output:
noncompliant: transferred 1000000
compliant:    rejected 1000000
Amount is final: true
Amount.of(500) = 500
*/
