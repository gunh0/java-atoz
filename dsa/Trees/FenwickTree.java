// Fenwick tree (binary indexed tree): prefix sums with point updates, both in
// O(log n), in a single array. Using 1-based indices, tree[i] stores the sum of
// the lowbit(i) = i & -i elements ending at i. A prefix query walks down by
// clearing the lowest set bit (i -= i & -i); an update walks up by adding it
// (i += i & -i). A range sum [l, r] is prefix(r) - prefix(l - 1). Compared with a
// plain prefix-sum array (O(1) query, O(n) update) it suits data that changes
// between queries; a segment tree does the same and more (min/max) at more code.
import java.util.Arrays;
import java.util.Random;

public class FenwickTree {
    private final long[] tree; // 1-based

    public FenwickTree(int n) {
        tree = new long[n + 1];
    }

    // O(n) construction: each node pushes its total to its parent once
    public FenwickTree(long[] values) {
        this(values.length);
        for (int i = 1; i <= values.length; i++) {
            tree[i] += values[i - 1];
            int parent = i + (i & -i);
            if (parent < tree.length) {
                tree[parent] += tree[i];
            }
        }
    }

    // a[index] += delta, index is 0-based for callers
    public void add(int index, long delta) {
        for (int i = index + 1; i < tree.length; i += i & -i) {
            tree[i] += delta;
        }
    }

    // sum of a[0..count)
    public long prefixSum(int count) {
        long sum = 0;
        for (int i = count; i > 0; i -= i & -i) {
            sum += tree[i];
        }
        return sum;
    }

    // sum of a[from..to], inclusive, 0-based
    public long rangeSum(int from, int to) {
        return prefixSum(to + 1) - prefixSum(from);
    }

    public static void main(String[] args) {
        long[] a = {5, 2, 9, -3, 7, 4, 0, 6};
        FenwickTree ft = new FenwickTree(a);
        System.out.println("values:       " + Arrays.toString(a));
        System.out.println("internal:     " + Arrays.toString(Arrays.copyOfRange(ft.tree, 1, ft.tree.length)));
        System.out.println("  tree[4] covers a[0..3], tree[6] covers a[4..5], tree[8] covers all");
        System.out.println("prefix(5):    " + ft.prefixSum(5));
        System.out.println("range[2..5]:  " + ft.rangeSum(2, 5));
        ft.add(3, 10); // a[3] = -3 -> 7
        System.out.println("after a[3] += 10, range[2..5]: " + ft.rangeSum(2, 5) + ", total: " + ft.prefixSum(8));

        // Index walk for prefixSum(7): 7 -> 6 -> 4 -> 0
        StringBuilder walk = new StringBuilder();
        for (int i = 7; i > 0; i -= i & -i) {
            walk.append(i).append(" (").append(Integer.toBinaryString(i)).append(") ");
        }
        System.out.println("prefix(7) visits tree[]: " + walk.toString().trim());

        // Self-check against a brute-force array under random updates and queries
        Random rnd = new Random(20);
        int n = 1000;
        long[] brute = new long[n];
        FenwickTree f = new FenwickTree(n);
        boolean ok = true;
        for (int op = 0; op < 20_000; op++) {
            if (rnd.nextBoolean()) {
                int i = rnd.nextInt(n);
                long d = rnd.nextInt(201) - 100;
                brute[i] += d;
                f.add(i, d);
            } else {
                int l = rnd.nextInt(n), r = l + rnd.nextInt(n - l);
                long expected = 0;
                for (int i = l; i <= r; i++) {
                    expected += brute[i];
                }
                ok &= f.rangeSum(l, r) == expected;
            }
        }
        long[] init = rnd.longs(n, -1000, 1000).toArray();
        FenwickTree built = new FenwickTree(init);
        FenwickTree added = new FenwickTree(n);
        for (int i = 0; i < n; i++) {
            added.add(i, init[i]);
        }
        ok &= Arrays.equals(built.tree, added.tree);
        System.out.println("20000 random ops match brute force, O(n) build matches: " + ok);
    }
}

/* Output:
values:       [5, 2, 9, -3, 7, 4, 0, 6]
internal:     [5, 7, 9, 13, 7, 11, 0, 30]
  tree[4] covers a[0..3], tree[6] covers a[4..5], tree[8] covers all
prefix(5):    20
range[2..5]:  17
after a[3] += 10, range[2..5]: 27, total: 40
prefix(7) visits tree[]: 7 (111) 6 (110) 4 (100)
20000 random ops match brute force, O(n) build matches: true
*/
