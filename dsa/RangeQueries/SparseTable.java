// A sparse table answers range-minimum queries (RMQ) on a static array in
// O(1) after O(n log n) preprocessing. table[j][i] holds min(a[i .. i+2^j-1]),
// built level by level: a block of length 2^j is two blocks of length 2^(j-1).
// A query [l, r] picks the largest power of two 2^j <= r-l+1 and combines the
// two (possibly overlapping) blocks starting at l and ending at r. Overlap is
// harmless because min is idempotent: min(x, x) = x. The same table works for
// max, gcd, bitwise and/or, but NOT for sum (overlap would count twice; use a
// prefix-sum array or a Fenwick tree there). No updates: one changed element
// invalidates O(log n) entries per level, so mutable data wants a segment
// tree. Floor log2 comes from Integer.numberOfLeadingZeros, no floating point.
import java.util.Arrays;
import java.util.Random;
import java.util.function.IntBinaryOperator;

public class SparseTable {
    private final int[][] table;
    private final IntBinaryOperator op;

    SparseTable(int[] a, IntBinaryOperator op) {
        this.op = op;
        int n = a.length;
        int levels = 32 - Integer.numberOfLeadingZeros(Math.max(n, 1));
        table = new int[levels][];
        table[0] = a.clone();
        for (int j = 1; j < levels; j++) {
            int half = 1 << (j - 1);
            int len = n - (1 << j) + 1;
            table[j] = new int[len];
            for (int i = 0; i < len; i++) {
                table[j][i] = op.applyAsInt(table[j - 1][i], table[j - 1][i + half]);
            }
        }
    }

    static int floorLog2(int x) {
        return 31 - Integer.numberOfLeadingZeros(x);
    }

    // op over a[l..r], inclusive; valid only for idempotent operations
    int query(int l, int r) {
        int j = floorLog2(r - l + 1);
        return op.applyAsInt(table[j][l], table[j][r - (1 << j) + 1]);
    }

    int memoryCells() {
        return Arrays.stream(table).mapToInt(row -> row.length).sum();
    }

    static int gcd(int a, int b) {
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return Math.abs(a);
    }

    static int brute(int[] a, int l, int r, IntBinaryOperator op) {
        int acc = a[l];
        for (int i = l + 1; i <= r; i++) {
            acc = op.applyAsInt(acc, a[i]);
        }
        return acc;
    }

    public static void main(String[] args) {
        int[] temps = {7, 2, 9, 4, 4, 8, 1, 6, 3, 5};
        SparseTable min = new SparseTable(temps, Math::min);
        SparseTable max = new SparseTable(temps, Math::max);
        System.out.println("a = " + Arrays.toString(temps));
        for (int j = 0; j < min.table.length; j++) {
            System.out.println("min of blocks of " + (1 << j) + ": " + Arrays.toString(min.table[j]));
        }
        for (int[] q : new int[][] {{0, 9}, {2, 5}, {3, 3}, {7, 9}}) {
            System.out.printf("[%d, %d] min %d max %d (two blocks of %d)%n",
                    q[0], q[1], min.query(q[0], q[1]), max.query(q[0], q[1]), 1 << floorLog2(q[1] - q[0] + 1));
        }

        // A sum table gives wrong answers whenever the two blocks overlap
        SparseTable sum = new SparseTable(temps, Integer::sum);
        System.out.println("sum [0, 2] via sparse table: " + sum.query(0, 2) + ", real sum: " + brute(temps, 0, 2, Integer::sum));

        // Self-check against brute force on random arrays (min and gcd)
        Random rnd = new Random(5);
        boolean ok = true;
        long queries = 0;
        for (int trial = 0; trial < 50; trial++) {
            int n = 1 + rnd.nextInt(300);
            int[] a = rnd.ints(n, -1000, 1000).toArray();
            int[] g = rnd.ints(n, 1, 50).map(x -> x * 6).toArray();
            SparseTable tMin = new SparseTable(a, Math::min);
            SparseTable tGcd = new SparseTable(g, SparseTable::gcd);
            for (int k = 0; k < 500; k++) {
                int l = rnd.nextInt(n);
                int r = l + rnd.nextInt(n - l);
                ok &= tMin.query(l, r) == brute(a, l, r, Math::min);
                ok &= tGcd.query(l, r) == brute(g, l, r, SparseTable::gcd);
                queries += 2;
            }
        }
        System.out.println(queries + " random min/gcd queries match brute force: " + ok);
        int n = 100_000;
        SparseTable big = new SparseTable(new Random(1).ints(n).toArray(), Math::min);
        System.out.printf("n = %d: %d levels, %d cells (about n log2 n = %d)%n",
                n, big.table.length, big.memoryCells(), (long) n * floorLog2(n));
    }
}

/* Output:
a = [7, 2, 9, 4, 4, 8, 1, 6, 3, 5]
min of blocks of 1: [7, 2, 9, 4, 4, 8, 1, 6, 3, 5]
min of blocks of 2: [2, 2, 4, 4, 4, 1, 1, 3, 3]
min of blocks of 4: [2, 2, 4, 1, 1, 1, 1]
min of blocks of 8: [1, 1, 1]
[0, 9] min 1 max 9 (two blocks of 8)
[2, 5] min 4 max 9 (two blocks of 4)
[3, 3] min 4 max 4 (two blocks of 1)
[7, 9] min 3 max 6 (two blocks of 2)
sum [0, 2] via sparse table: 20, real sum: 18
50000 random min/gcd queries match brute force: true
n = 100000: 17 levels, 1568946 cells (about n log2 n = 1600000)
*/
