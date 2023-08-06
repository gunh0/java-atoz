// NUM12-J. Ensure conversions of numeric types to narrower types do not result in lost or misinterpreted data
// https://wiki.sei.cmu.edu/confluence/display/java/NUM12-J.+Ensure+conversions+of+numeric+types+to+narrower+types+do+not+result+in+lost+or+misinterpreted+data
//
// A narrowing primitive conversion such as (int) aLong or (byte) anInt keeps
// only the low-order bits, so the value can change magnitude and even sign.
// (int) on a double rounds toward zero and saturates at Integer.MIN_VALUE /
// MAX_VALUE, and NaN becomes 0. If a check is done on the wide value and
// the narrowed value is used afterwards (or the other way round), the two
// disagree. Range-check before narrowing, or use Math.toIntExact(), which
// throws instead of losing data.
public class NUM12_NarrowingConversions {
    public static void main(String[] args) {
        System.out.println("(byte) 200          = " + (byte) 200);
        System.out.println("(int) 4294967297L   = " + (int) 4_294_967_297L);
        System.out.println("(int) 1e10          = " + (int) 1e10);
        System.out.println("(int) Double.NaN    = " + (int) Double.NaN);

        // A file upload declares its length as a long; a buffer is allocated with an int
        long[] declared = {1_024, 4_294_967_396L};
        for (long length : declared) {
            System.out.println("declared length " + length);
            System.out.println("  noncompliant: " + allocateNoncompliant(length));
            System.out.println("  compliant:    " + allocateCompliant(length));
        }
    }

    // Noncompliant: the narrowed value (100) is what gets used, while the
    // client goes on to send 4 GB into a 100-byte buffer
    static String allocateNoncompliant(long length) {
        if (length < 0) {
            return "rejected";
        }
        int size = (int) length;
        return "buffer of " + new byte[size].length + " bytes";
    }

    // Compliant: refuse values that do not fit (and enforce a real limit)
    static String allocateCompliant(long length) {
        try {
            int size = Math.toIntExact(length);
            if (size < 0 || size > 10_000_000) {
                return "rejected (out of range)";
            }
            return "buffer of " + new byte[size].length + " bytes";
        } catch (ArithmeticException e) {
            return "rejected (" + e.getMessage() + ")";
        }
    }
}

/* Output:
(byte) 200          = -56
(int) 4294967297L   = 1
(int) 1e10          = 2147483647
(int) Double.NaN    = 0
declared length 1024
  noncompliant: buffer of 1024 bytes
  compliant:    buffer of 1024 bytes
declared length 4294967396
  noncompliant: buffer of 100 bytes
  compliant:    rejected (integer overflow)
*/
