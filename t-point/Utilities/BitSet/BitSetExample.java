// java.util.BitSet is a growable vector of bits, packed 64 per long.
// It is the compact way to store a set of small non-negative ints or a
// large array of flags: one bit per element instead of a Boolean object.
//  - set / clear / flip / get work on single bits or ranges [from, to)
//  - and / or / xor / andNot combine two sets in place (word at a time)
//  - cardinality() counts set bits; length() is highest set bit + 1;
//    size() is the allocated capacity in bits (an implementation detail)
//  - iterate with nextSetBit(i) or stream()
// BitSet is not thread-safe.
import java.util.BitSet;

public class BitSetExample {

    // Sieve of Eratosthenes: bit i set <=> i is composite
    static BitSet primesUpTo(int n) {
        BitSet composite = new BitSet(n + 1);
        for (int p = 2; (long) p * p <= n; p = composite.nextClearBit(p + 1)) {
            for (int m = p * p; m <= n; m += p) {
                composite.set(m);
            }
        }
        BitSet primes = new BitSet(n + 1);
        primes.set(2, n + 1);
        primes.andNot(composite);
        return primes;
    }

    public static void main(String[] args) {
        BitSet bits = new BitSet();
        bits.set(1);
        bits.set(3);
        bits.set(5, 9);      // 5, 6, 7, 8
        bits.clear(7);
        bits.flip(0);        // was 0 -> now 1
        System.out.println("bits: " + bits);
        System.out.println("get(3)=" + bits.get(3) + ", get(7)=" + bits.get(7));
        System.out.println("cardinality=" + bits.cardinality() + ", length=" + bits.length());

        // Iterating set bits
        StringBuilder sb = new StringBuilder();
        for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
            sb.append(i).append(' ');
        }
        System.out.println("nextSetBit loop: " + sb.toString().trim());
        System.out.println("first clear bit: " + bits.nextClearBit(0));

        // Set algebra: which days are both team A and team B on call?
        BitSet teamA = BitSet.valueOf(new long[] {0b0011111L}); // Mon..Fri (bits 0-4)
        BitSet teamB = new BitSet();
        teamB.set(3, 7);                                         // Thu..Sun (bits 3-6)
        BitSet both = (BitSet) teamA.clone();
        both.and(teamB);
        BitSet either = (BitSet) teamA.clone();
        either.or(teamB);
        BitSet onlyOne = (BitSet) teamA.clone();
        onlyOne.xor(teamB);
        System.out.println("A=" + teamA + " B=" + teamB);
        System.out.println("and=" + both + " or=" + either + " xor=" + onlyOne);
        System.out.println("intersects: " + teamA.intersects(teamB));

        // Primes with the sieve
        BitSet primes = primesUpTo(100);
        System.out.println("primes <= 100: " + primes.cardinality());
        System.out.println("primes <= 30:  " + primes.get(0, 31));
        System.out.println("sum via stream: " + primes.stream().sum());

        // Converting to and from long[] / byte[] (little-endian bit order)
        BitSet flags = new BitSet();
        flags.set(0);
        flags.set(9);
        System.out.println("toLongArray: " + Long.toBinaryString(flags.toLongArray()[0]));
        byte[] bytes = flags.toByteArray();
        System.out.printf("toByteArray: %d bytes, [0]=%d, [1]=%d%n", bytes.length, bytes[0], bytes[1]);
        System.out.println("round trip equal: " + BitSet.valueOf(bytes).equals(flags));

        // A million flags in about 122 KiB instead of a boolean[] of ~1 MiB
        BitSet seen = new BitSet(1_000_000);
        for (int i = 0; i < 1_000_000; i += 3) {
            seen.set(i);
        }
        System.out.println("multiples of 3 below 1e6: " + seen.cardinality());
    }
}

/* Output:
bits: {0, 1, 3, 5, 6, 8}
get(3)=true, get(7)=false
cardinality=6, length=9
nextSetBit loop: 0 1 3 5 6 8
first clear bit: 2
A={0, 1, 2, 3, 4} B={3, 4, 5, 6}
and={3, 4} or={0, 1, 2, 3, 4, 5, 6} xor={0, 1, 2, 5, 6}
intersects: true
primes <= 100: 25
primes <= 30:  {2, 3, 5, 7, 11, 13, 17, 19, 23, 29}
sum via stream: 1060
toLongArray: 1000000001
toByteArray: 2 bytes, [0]=1, [1]=2
round trip equal: true
multiples of 3 below 1e6: 333334
*/
