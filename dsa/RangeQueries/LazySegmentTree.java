// A segment tree stores an aggregate (here sum and min) for every segment in
// a recursive halving of the array: node 1 covers [0, n), its children cover
// the two halves, and so on, about 4n nodes in an array. A range query
// combines O(log n) nodes that exactly tile the range. With lazy propagation
// range UPDATES are O(log n) too: "add v to [l, r]" stops at every node whose
// segment lies fully inside the range, fixes that node's sum (+= v * length)
// and min (+= v), and writes v into the node's pending tag instead of visiting
// its whole subtree. The tag is pushed down to the children only when a later
// query or update needs to go below that node. Without laziness one range add
// would touch O(n) nodes. Compared with a Fenwick tree it supports any
// associative aggregate (min, max, sum together), at a larger constant factor.
import java.util.Arrays;
import java.util.Random;

public class LazySegmentTree {
    private final int n;
    private final long[] sum;
    private final long[] min;
    private final long[] lazy; // pending add not yet pushed down to the node's children
    int nodesVisited;

    LazySegmentTree(long[] a) {
        n = a.length;
        sum = new long[4 * n];
        min = new long[4 * n];
        lazy = new long[4 * n];
        build(1, 0, n - 1, a);
    }

    private void build(int node, int lo, int hi, long[] a) {
        if (lo == hi) {
            sum[node] = a[lo];
            min[node] = a[lo];
            return;
        }
        int mid = (lo + hi) >>> 1;
        build(2 * node, lo, mid, a);
        build(2 * node + 1, mid + 1, hi, a);
        pull(node);
    }

    private void pull(int node) {
        sum[node] = sum[2 * node] + sum[2 * node + 1];
        min[node] = Math.min(min[2 * node], min[2 * node + 1]);
    }

    // Apply "add v" to a whole segment: O(1), remember it for the children
    private void apply(int node, int lo, int hi, long v) {
        sum[node] += v * (hi - lo + 1);
        min[node] += v;
        lazy[node] += v;
    }

    private void push(int node, int lo, int hi) {
        if (lazy[node] != 0) {
            int mid = (lo + hi) >>> 1;
            apply(2 * node, lo, mid, lazy[node]);
            apply(2 * node + 1, mid + 1, hi, lazy[node]);
            lazy[node] = 0;
        }
    }

    void add(int l, int r, long v) {
        add(1, 0, n - 1, l, r, v);
    }

    private void add(int node, int lo, int hi, int l, int r, long v) {
        nodesVisited++;
        if (r < lo || hi < l) return;
        if (l <= lo && hi <= r) { // fully covered: stop here
            apply(node, lo, hi, v);
            return;
        }
        push(node, lo, hi);
        int mid = (lo + hi) >>> 1;
        add(2 * node, lo, mid, l, r, v);
        add(2 * node + 1, mid + 1, hi, l, r, v);
        pull(node);
    }

    long sum(int l, int r) {
        return sum(1, 0, n - 1, l, r);
    }

    private long sum(int node, int lo, int hi, int l, int r) {
        nodesVisited++;
        if (r < lo || hi < l) return 0;
        if (l <= lo && hi <= r) return sum[node];
        push(node, lo, hi);
        int mid = (lo + hi) >>> 1;
        return sum(2 * node, lo, mid, l, r) + sum(2 * node + 1, mid + 1, hi, l, r);
    }

    long min(int l, int r) {
        return min(1, 0, n - 1, l, r);
    }

    private long min(int node, int lo, int hi, int l, int r) {
        nodesVisited++;
        if (r < lo || hi < l) return Long.MAX_VALUE;
        if (l <= lo && hi <= r) return min[node];
        push(node, lo, hi);
        int mid = (lo + hi) >>> 1;
        return Math.min(min(2 * node, lo, mid, l, r), min(2 * node + 1, mid + 1, hi, l, r));
    }

    public static void main(String[] args) {
        // Seats sold per row of a theatre; group bookings add to ranges of rows
        long[] seats = {5, 3, 8, 6, 2, 7, 4, 1};
        LazySegmentTree t = new LazySegmentTree(seats);
        System.out.println("rows " + Arrays.toString(seats));
        System.out.println("sum [0, 7] = " + t.sum(0, 7) + ", min [2, 5] = " + t.min(2, 5));
        t.add(1, 4, 10);
        System.out.println("after +10 on rows 1..4: sum [0, 7] = " + t.sum(0, 7)
                + ", sum [3, 6] = " + t.sum(3, 6) + ", min [0, 4] = " + t.min(0, 4));
        t.add(0, 7, -1);
        System.out.println("after -1 on all rows:   sum [0, 7] = " + t.sum(0, 7) + ", min [0, 7] = " + t.min(0, 7));

        // Self-check against a plain array with O(n) updates and queries
        Random rnd = new Random(77);
        boolean ok = true;
        for (int trial = 0; trial < 20; trial++) {
            int n = 1 + rnd.nextInt(500);
            long[] a = rnd.longs(n, -1_000, 1_000).toArray();
            LazySegmentTree tree = new LazySegmentTree(a);
            for (int op = 0; op < 2_000; op++) {
                int l = rnd.nextInt(n);
                int r = l + rnd.nextInt(n - l);
                switch (rnd.nextInt(3)) {
                    case 0 -> {
                        long v = rnd.nextInt(201) - 100;
                        tree.add(l, r, v);
                        for (int i = l; i <= r; i++) a[i] += v;
                    }
                    case 1 -> ok &= tree.sum(l, r) == Arrays.stream(a, l, r + 1).sum();
                    default -> ok &= tree.min(l, r) == Arrays.stream(a, l, r + 1).min().orElseThrow();
                }
            }
        }
        System.out.println("40000 random range adds/sums/mins match brute force: " + ok);

        // Cost of one range update: O(log n) nodes, not O(n)
        int n = 1 << 20;
        LazySegmentTree big = new LazySegmentTree(new long[n]);
        big.nodesVisited = 0;
        big.add(12_345, 987_654, 1);
        System.out.println("add over 975310 of " + n + " elements visited " + big.nodesVisited + " nodes");
        System.out.println("sum after: " + big.sum(0, n - 1));
    }
}

/* Output:
rows [5, 3, 8, 6, 2, 7, 4, 1]
sum [0, 7] = 36, min [2, 5] = 2
after +10 on rows 1..4: sum [0, 7] = 76, sum [3, 6] = 39, min [0, 4] = 5
after -1 on all rows:   sum [0, 7] = 68, min [0, 7] = 0
40000 random range adds/sums/mins match brute force: true
add over 975310 of 1048576 elements visited 79 nodes
sum after: 975310
*/
