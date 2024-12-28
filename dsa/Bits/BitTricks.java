// Bit manipulation tricks that come up in hashing, allocators, permission masks
// and interview problems. All rely on two's complement arithmetic:
//  - x & -x isolates the lowest set bit; x & (x - 1) clears it
//  - x > 0 && (x & (x - 1)) == 0 tests for a power of two
//  - Kernighan's popcount clears one set bit per iteration
//  - the subsets of a mask are enumerated with sub = (sub - 1) & mask
//  - XOR cancels pairs: a ^ a = 0, which finds a missing or unpaired number
// Every hand-rolled version is checked against the Integer/Long built-ins
// (bitCount, lowestOneBit, highestOneBit, numberOfTrailingZeros, reverse).
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BitTricks {
    static int lowestSetBit(int x) {
        return x & -x;
    }

    static int clearLowestSetBit(int x) {
        return x & (x - 1);
    }

    static boolean isPowerOfTwo(int x) {
        return x > 0 && (x & (x - 1)) == 0;
    }

    static int popcount(int x) { // Kernighan: one iteration per set bit
        int count = 0;
        while (x != 0) {
            x &= x - 1;
            count++;
        }
        return count;
    }

    static int nextPowerOfTwo(int x) { // smallest power of two >= x, for 1 <= x <= 2^30
        x--;
        x |= x >>> 1;
        x |= x >>> 2;
        x |= x >>> 4;
        x |= x >>> 8;
        x |= x >>> 16;
        return x + 1;
    }

    static int trailingZeros(int x) {
        if (x == 0) {
            return 32;
        }
        int n = 0;
        while ((x & 1) == 0) {
            x >>>= 1;
            n++;
        }
        return n;
    }

    static int reverseBits(int x) {
        int r = 0;
        for (int i = 0; i < 32; i++) {
            r = (r << 1) | (x & 1);
            x >>>= 1;
        }
        return r;
    }

    static List<Integer> subsets(int mask) {
        List<Integer> out = new ArrayList<>();
        for (int sub = mask; ; sub = (sub - 1) & mask) {
            out.add(sub);
            if (sub == 0) {
                return out;
            }
        }
    }

    static int missingNumber(int[] values) { // values hold 0..n with one missing
        int x = values.length;
        for (int i = 0; i < values.length; i++) {
            x ^= i ^ values[i];
        }
        return x;
    }

    static String bin(int x) {
        return String.format("%8s", Integer.toBinaryString(x)).replace(' ', '0');
    }

    public static void main(String[] args) {
        int x = 0b1011_0100;
        System.out.println("x                   = " + bin(x) + " (" + x + ")");
        System.out.println("x & -x   lowest bit = " + bin(lowestSetBit(x)));
        System.out.println("x & (x-1) cleared   = " + bin(clearLowestSetBit(x)));
        System.out.println("popcount            = " + popcount(x));
        System.out.println("trailing zeros      = " + trailingZeros(x));
        System.out.println("powers of two among 0, 1, 6, 64, -8: "
                + isPowerOfTwo(0) + " " + isPowerOfTwo(1) + " " + isPowerOfTwo(6) + " " + isPowerOfTwo(64) + " " + isPowerOfTwo(-8));
        System.out.println("next power of two for 5, 16, 1000: "
                + nextPowerOfTwo(5) + " " + nextPowerOfTwo(16) + " " + nextPowerOfTwo(1000));

        // Permission flags packed into an int
        final int READ = 1, WRITE = 1 << 1, EXEC = 1 << 2;
        int perms = READ | EXEC;
        System.out.println("perms r-x: canWrite=" + ((perms & WRITE) != 0) + ", after grant+revoke: "
                + bin((perms | WRITE) & ~EXEC).substring(5));
        System.out.println("subsets of 101: " + subsets(0b101).stream().map(Integer::toBinaryString).toList());
        System.out.println("missing number in [3,0,1,5,2]: " + missingNumber(new int[] {3, 0, 1, 5, 2}));

        int a = 12, b = 34;
        a ^= b;
        b ^= a;
        a ^= b; // XOR swap: a curiosity, a temp variable is clearer and as fast
        System.out.println("xor swap: a=" + a + " b=" + b);

        // Self-check against the JDK intrinsics
        Random rnd = new Random(25);
        boolean ok = true;
        for (int t = 0; t < 100_000; t++) {
            int v = rnd.nextInt();
            ok &= popcount(v) == Integer.bitCount(v);
            ok &= lowestSetBit(v) == Integer.lowestOneBit(v);
            ok &= trailingZeros(v) == Integer.numberOfTrailingZeros(v);
            ok &= reverseBits(v) == Integer.reverse(v);
            ok &= isPowerOfTwo(v) == (v > 0 && Integer.bitCount(v) == 1);
            int p = 1 + (v >>> 2) % (1 << 30);
            int expected = Integer.highestOneBit(p) == p ? p : Integer.highestOneBit(p) << 1;
            ok &= nextPowerOfTwo(p) == expected;
        }
        ok &= subsets(0b1011_0110).size() == 1 << Integer.bitCount(0b1011_0110);
        System.out.println("100000 random ints match Integer.bitCount/lowestOneBit/numberOfTrailingZeros/reverse: " + ok);
    }
}

/* Output:
x                   = 10110100 (180)
x & -x   lowest bit = 00000100
x & (x-1) cleared   = 10110000
popcount            = 4
trailing zeros      = 2
powers of two among 0, 1, 6, 64, -8: false true false true false
next power of two for 5, 16, 1000: 8 16 1024
perms r-x: canWrite=false, after grant+revoke: 011
subsets of 101: [101, 100, 1, 0]
missing number in [3,0,1,5,2]: 4
xor swap: a=34 b=12
100000 random ints match Integer.bitCount/lowestOneBit/numberOfTrailingZeros/reverse: true
*/
