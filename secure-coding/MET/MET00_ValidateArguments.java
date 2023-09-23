// MET00-J. Validate method arguments
// https://wiki.sei.cmu.edu/confluence/display/java/MET00-J.+Validate+method+arguments
//
// A method is a trust boundary in miniature: its caller may pass null,
// negative sizes, out-of-range indexes or values that break the object's
// invariants. A method that stores such arguments without checking them
// fails later, far from the cause, or worse, keeps working on corrupted
// state. Validate arguments at the entry of every public (and, where
// callers are not fully trusted, package-private) method, reject bad ones
// with a clear exception before changing any state, and use the JDK
// helpers: Objects.requireNonNull, Objects.checkIndex,
// Objects.checkFromIndexSize.
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

public class MET00_ValidateArguments {
    public static void main(String[] args) {
        byte[] packet = "HEADER--PAYLOAD!".getBytes(StandardCharsets.US_ASCII);

        // Noncompliant: a negative offset is stored and only fails on use
        WindowNoncompliant bad = new WindowNoncompliant();
        bad.set(packet, 8, 7);
        System.out.println("noncompliant, valid window:   " + new String(bad.read(), StandardCharsets.US_ASCII));
        bad.set(packet, -2, 4);
        try {
            System.out.println("noncompliant, offset -2:      " + Arrays.toString(bad.read()));
        } catch (RuntimeException e) {
            System.out.println("noncompliant, offset -2:      failed later with " + e.getClass().getSimpleName()
                    + ", the window now holds offset " + bad.offset);
        }

        // Compliant: rejected up front, previous state untouched
        WindowCompliant good = new WindowCompliant();
        good.set(packet, 8, 7);
        try {
            good.set(packet, -2, 4);
        } catch (IndexOutOfBoundsException e) {
            System.out.println("compliant,    offset -2:      rejected (" + e.getMessage() + ")");
        }
        try {
            good.set(null, 0, 0);
        } catch (NullPointerException e) {
            System.out.println("compliant,    null data:      rejected (" + e.getMessage() + ")");
        }
        System.out.println("compliant,    window still:   " + new String(good.read(), StandardCharsets.US_ASCII));
    }
}

// Noncompliant: no checks at all
class WindowNoncompliant {
    byte[] data;
    int offset, length;

    void set(byte[] data, int offset, int length) {
        this.data = data;
        this.offset = offset;
        this.length = length;
    }

    byte[] read() {
        return Arrays.copyOfRange(data, offset, offset + length);
    }
}

// Compliant: validates everything before assigning anything
class WindowCompliant {
    private byte[] data;
    private int offset, length;

    void set(byte[] data, int offset, int length) {
        Objects.requireNonNull(data, "data");
        Objects.checkFromIndexSize(offset, length, data.length);
        this.data = data;
        this.offset = offset;
        this.length = length;
    }

    byte[] read() {
        return Arrays.copyOfRange(data, offset, offset + length);
    }
}

/* Output:
noncompliant, valid window:   PAYLOAD
noncompliant, offset -2:      failed later with ArrayIndexOutOfBoundsException, the window now holds offset -2
compliant,    offset -2:      rejected (Range [-2, -2 + 4) out of bounds for length 16)
compliant,    null data:      rejected (data)
compliant,    window still:   PAYLOAD
*/
