// A Bloom filter answers "have I seen this key?" in a fixed number of bits,
// with no false negatives and a tunable rate of false positives. It is an
// array of m bits and k hash functions: add() sets the k bits a key hashes
// to, mightContain() reports true only if all k are set. Bits are never
// cleared, so a key that was added always answers true; a key that was not
// added answers true only if other keys happened to set all its k bits.
// For n keys the false-positive rate is about (1 - e^(-k*n/m))^k, minimized
// by k = (m/n) ln 2; for a target rate p, m = -n ln p / (ln 2)^2. Instead of
// k independent hashes, double hashing h1 + i*h2 (Kirsch-Mitzenmacher) gives
// the same asymptotic rate from one 64-bit hash. add and query are O(k);
// space is m bits whatever the size of the keys. Typical use: skip an
// expensive lookup (disk, network) when the filter says "definitely not".
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class BloomFilter {
    private final long[] bits;
    private final int m;
    private final int k;

    BloomFilter(int m, int k) {
        this.m = m;
        this.k = k;
        this.bits = new long[(m + 63) / 64];
    }

    // Size for n expected keys and a target false-positive probability p
    static BloomFilter forCapacity(int n, double p) {
        int m = (int) Math.ceil(-n * Math.log(p) / (Math.log(2) * Math.log(2)));
        int k = Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
        return new BloomFilter(m, k);
    }

    // 64-bit FNV-1a followed by a finalizer mix, so both 32-bit halves are well spread
    static long hash64(String key) {
        long h = 0xcbf29ce484222325L;
        for (byte b : key.getBytes(StandardCharsets.UTF_8)) {
            h ^= b & 0xff;
            h *= 0x100000001b3L;
        }
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }

    private int index(long h, int i) {
        int h1 = (int) h;
        int h2 = (int) (h >>> 32) | 1; // odd, so the k probes differ
        return Math.floorMod(h1 + i * h2, m);
    }

    void add(String key) {
        long h = hash64(key);
        for (int i = 0; i < k; i++) {
            int idx = index(h, i);
            bits[idx >>> 6] |= 1L << idx;
        }
    }

    boolean mightContain(String key) {
        long h = hash64(key);
        for (int i = 0; i < k; i++) {
            int idx = index(h, i);
            if ((bits[idx >>> 6] & (1L << idx)) == 0) {
                return false; // one clear bit proves the key was never added
            }
        }
        return true;
    }

    int bitCount() {
        int c = 0;
        for (long w : bits) {
            c += Long.bitCount(w);
        }
        return c;
    }

    static double expectedRate(int m, int k, int n) {
        return Math.pow(1 - Math.exp(-(double) k * n / m), k);
    }

    public static void main(String[] args) {
        int n = 10_000;
        for (double target : new double[] {0.05, 0.01, 0.001}) {
            BloomFilter f = forCapacity(n, target);
            Set<String> added = new HashSet<>();
            for (int i = 0; i < n; i++) {
                String key = "user-" + i;
                f.add(key);
                added.add(key);
            }
            // No false negatives: every added key must be reported
            boolean allFound = added.stream().allMatch(f::mightContain);
            // False positives: probe keys that were never added
            int probes = 100_000;
            int falsePositives = 0;
            for (int i = 0; i < probes; i++) {
                if (f.mightContain("other-" + i)) {
                    falsePositives++;
                }
            }
            System.out.printf(Locale.ROOT, "target %.3f: m=%d bits (%.1f bits/key), k=%d, fill %.2f%n",
                    target, f.m, (double) f.m / n, f.k, (double) f.bitCount() / f.m);
            System.out.printf(Locale.ROOT, "  no false negatives: %b, measured FP %.4f, formula %.4f%n",
                    allFound, (double) falsePositives / probes, expectedRate(f.m, f.k, n));
        }

        // Too many keys for the size: the rate degrades quickly
        BloomFilter small = new BloomFilter(8_192, 4);
        for (int i = 0; i < 5_000; i++) {
            small.add("user-" + i);
        }
        int fp = 0;
        for (int i = 0; i < 10_000; i++) {
            fp += small.mightContain("other-" + i) ? 1 : 0;
        }
        System.out.printf(Locale.ROOT, "overfilled (m=8192, k=4, n=5000): measured FP %.3f, formula %.3f%n",
                fp / 10_000.0, expectedRate(8_192, 4, 5_000));
    }
}

/* Output:
target 0.050: m=62353 bits (6.2 bits/key), k=4, fill 0.47
  no false negatives: true, measured FP 0.0505, formula 0.0503
target 0.010: m=95851 bits (9.6 bits/key), k=7, fill 0.52
  no false negatives: true, measured FP 0.0099, formula 0.0100
target 0.001: m=143776 bits (14.4 bits/key), k=10, fill 0.50
  no false negatives: true, measured FP 0.0009, formula 0.0010
overfilled (m=8192, k=4, n=5000): measured FP 0.694, formula 0.695
*/
