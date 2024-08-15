// NUM14-J. Use shift operators correctly
// https://wiki.sei.cmu.edu/confluence/display/java/NUM14-J.+Use+shift+operators+correctly
//
// Java masks the shift distance: for an int only the low 5 bits count
// (distance & 31), for a long the low 6 bits (distance & 63). So 1 << 32
// is 1, not 0, and 1 << 40 is 256. And >> on a negative value copies the
// sign bit, while >>> shifts in zeros; bytes are sign-extended to int
// before shifting. Code that builds bit masks from untrusted indexes, or
// extracts fields from packed data, silently gets the wrong bits. Validate
// shift distances, use long (1L << n) when you need more than 31 bits,
// use >>> for unsigned data, and mask bytes with & 0xFF before shifting.
public class NUM14_ShiftOperators {
    public static void main(String[] args) {
        // Permission flags 0..63 stored in a long; the flag index comes from a request
        int flag = 33;
        long noncompliantMask = 1 << flag;   // int shift: 33 & 31 = 1
        long compliantMask = 1L << flag;     // long shift
        System.out.println("noncompliant: mask for flag 33 = 0x" + Long.toHexString(noncompliantMask) + " (that is flag 1)");
        System.out.println("compliant:    mask for flag 33 = 0x" + Long.toHexString(compliantMask));
        System.out.println("compliant:    flag 64 accepted? " + validFlag(64));

        // Distances are masked, not saturated
        System.out.println("1 << 32 = " + (1 << 32) + ", 1 << 40 = " + (1 << 40) + ", 1L << 64 = " + (1L << 64));

        // Extracting the high byte of an int holding unsigned data
        int packed = 0xF0_12_34_56;
        System.out.println("noncompliant: packed >> 24  = " + (packed >> 24));
        System.out.println("compliant:    packed >>> 24 = " + (packed >>> 24));

        // Bytes are sign-extended before shifting
        byte b = (byte) 0x9C;
        System.out.println("noncompliant: high nibble b >> 4          = " + (b >> 4));
        System.out.println("compliant:    high nibble (b & 0xFF) >> 4 = " + ((b & 0xFF) >> 4));

        // Assembling a 16-bit big-endian length from two bytes
        byte hi = (byte) 0x01, lo = (byte) 0xF4;
        System.out.println("noncompliant: (hi << 8) | lo = " + ((hi << 8) | lo));
        System.out.println("compliant:    ((hi & 0xFF) << 8) | (lo & 0xFF) = " + (((hi & 0xFF) << 8) | (lo & 0xFF)));
    }

    static boolean validFlag(int index) {
        return index >= 0 && index < Long.SIZE;
    }
}

/* Output:
noncompliant: mask for flag 33 = 0x2 (that is flag 1)
compliant:    mask for flag 33 = 0x200000000
compliant:    flag 64 accepted? false
1 << 32 = 1, 1 << 40 = 256, 1L << 64 = 1
noncompliant: packed >> 24  = -16
compliant:    packed >>> 24 = 240
noncompliant: high nibble b >> 4          = -7
compliant:    high nibble (b & 0xFF) >> 4 = 9
noncompliant: (hi << 8) | lo = -12
compliant:    ((hi & 0xFF) << 8) | (lo & 0xFF) = 500
*/
