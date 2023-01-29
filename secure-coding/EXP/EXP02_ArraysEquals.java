// EXP02-J. Do not use the Object.equals() method to compare two arrays
// https://wiki.sei.cmu.edu/confluence/display/java/EXP02-J.+Do+not+use+the+Object.equals()+method+to+compare+two+arrays
//
// Arrays do not override equals(), so a.equals(b) is the same as a == b: it
// asks "is this the same array object?", not "do they hold the same values?".
// Code that compares a submitted PIN, a MAC or a password hash this way
// rejects every correct value (and == has the same problem). Compare
// contents with Arrays.equals() (Arrays.deepEquals() for nested arrays), and
// for secrets use MessageDigest.isEqual(), whose running time does not
// depend on where the first mismatch is.
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

public class EXP02_ArraysEquals {
    private static final byte[] STORED_MAC = "expected-mac".getBytes(StandardCharsets.US_ASCII);

    public static void main(String[] args) {
        byte[] submitted = "expected-mac".getBytes(StandardCharsets.US_ASCII); // a fresh array, same bytes
        byte[] forged = "forged-mac!!".getBytes(StandardCharsets.US_ASCII);

        System.out.println("noncompliant, correct MAC accepted: " + verifyNoncompliant(submitted));
        System.out.println("compliant,    correct MAC accepted: " + verifyCompliant(submitted));
        System.out.println("compliant,    forged MAC accepted:  " + verifyCompliant(forged));

        // Arrays.equals() compares one level only; nested arrays need deepEquals()
        int[][] a = {{1, 2}, {3, 4}};
        int[][] b = {{1, 2}, {3, 4}};
        System.out.println("Arrays.equals on int[][]:     " + Arrays.equals(a, b));
        System.out.println("Arrays.deepEquals on int[][]: " + Arrays.deepEquals(a, b));
    }

    // Noncompliant: Object.equals() on arrays is reference identity
    static boolean verifyNoncompliant(byte[] mac) {
        return STORED_MAC.equals(mac);
    }

    // Compliant: compares the contents, in time independent of the contents
    static boolean verifyCompliant(byte[] mac) {
        return MessageDigest.isEqual(STORED_MAC, mac);
    }
}

/* Output:
noncompliant, correct MAC accepted: false
compliant,    correct MAC accepted: true
compliant,    forged MAC accepted:  false
Arrays.equals on int[][]:     false
Arrays.deepEquals on int[][]: true
*/
