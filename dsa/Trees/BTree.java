// A B-tree is a balanced search tree whose nodes hold many keys: with minimum
// degree t, every node except the root has t-1 .. 2t-1 sorted keys, and an
// inner node with j keys has j+1 children, the i-th child holding the keys
// between key i-1 and key i. All leaves are at the same depth, and the height
// is at most log_t((n+1)/2), so with t in the hundreds a billion keys fit in
// four or five levels. That is why databases and file systems use B-trees
// (B+ trees in practice): one node = one disk page, and a lookup reads only a
// handful of pages. Search: binary-search the keys in a node, descend into one
// child. Insert (CLRS, single pass): on the way down, split every full child
// (2t-1 keys) before entering it, pushing its median up into the parent, so
// the leaf reached always has room. The tree grows only at the root, when the
// root itself is split. Here t = 2 (a 2-3-4 tree) to keep the output small.
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

public class BTree {
    static final class Node {
        int n;            // number of keys in use
        final int[] keys;
        final Node[] children;
        boolean leaf = true;

        Node(int t) {
            keys = new int[2 * t - 1];
            children = new Node[2 * t];
        }
    }

    private final int t;
    private Node root;
    private int size;
    int splits;

    BTree(int t) {
        this.t = t;
        this.root = new Node(t);
    }

    boolean contains(int key) {
        Node x = root;
        while (true) {
            int i = Arrays.binarySearch(x.keys, 0, x.n, key);
            if (i >= 0) return true;
            if (x.leaf) return false;
            x = x.children[-i - 1]; // insertion point = child to descend into
        }
    }

    boolean add(int key) {
        if (contains(key)) return false; // keep it a set, like TreeSet
        Node r = root;
        if (r.n == 2 * t - 1) { // full root: the only way the tree gets taller
            Node s = new Node(t);
            s.leaf = false;
            s.children[0] = r;
            root = s;
            splitChild(s, 0);
        }
        insertNonFull(root, key);
        size++;
        return true;
    }

    // y = x.children[i] is full: move its upper t-1 keys to a new node z and its median up into x
    private void splitChild(Node x, int i) {
        splits++;
        Node y = x.children[i];
        Node z = new Node(t);
        z.leaf = y.leaf;
        z.n = t - 1;
        System.arraycopy(y.keys, t, z.keys, 0, t - 1);
        if (!y.leaf) {
            System.arraycopy(y.children, t, z.children, 0, t);
            Arrays.fill(y.children, t, 2 * t, null);
        }
        y.n = t - 1;
        // open slot i+1 in x's children and slot i in x's keys
        System.arraycopy(x.children, i + 1, x.children, i + 2, x.n - i);
        x.children[i + 1] = z;
        System.arraycopy(x.keys, i, x.keys, i + 1, x.n - i);
        x.keys[i] = y.keys[t - 1];
        x.n++;
    }

    private void insertNonFull(Node x, int key) {
        while (!x.leaf) {
            int i = -Arrays.binarySearch(x.keys, 0, x.n, key) - 1;
            if (x.children[i].n == 2 * t - 1) {
                splitChild(x, i);
                if (key > x.keys[i]) i++; // the median moved up; pick the right half
            }
            x = x.children[i];
        }
        int i = x.n - 1;
        while (i >= 0 && x.keys[i] > key) {
            x.keys[i + 1] = x.keys[i];
            i--;
        }
        x.keys[i + 1] = key;
        x.n++;
    }

    int height() {
        int h = 1;
        for (Node x = root; !x.leaf; x = x.children[0]) h++;
        return h;
    }

    List<Integer> keys() {
        List<Integer> out = new ArrayList<>();
        inOrder(root, out);
        return out;
    }

    private void inOrder(Node x, List<Integer> out) {
        for (int i = 0; i < x.n; i++) {
            if (!x.leaf) inOrder(x.children[i], out);
            out.add(x.keys[i]);
        }
        if (!x.leaf) inOrder(x.children[x.n], out);
    }

    // Level-by-level picture: [k1 k2] per node
    String levels() {
        StringBuilder sb = new StringBuilder();
        List<Node> level = List.of(root);
        while (!level.isEmpty()) {
            List<Node> next = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (Node x : level) {
                line.append(line.isEmpty() ? "" : " ").append('[');
                for (int i = 0; i < x.n; i++) {
                    line.append(i == 0 ? "" : " ").append(x.keys[i]);
                }
                line.append(']');
                if (!x.leaf) next.addAll(Arrays.asList(x.children).subList(0, x.n + 1));
            }
            sb.append(line).append('\n');
            level = next;
        }
        return sb.toString();
    }

    // Every node within its key bounds, all leaves at one depth
    boolean valid() {
        return depthIfValid(root, true, Long.MIN_VALUE, Long.MAX_VALUE) > 0;
    }

    private int depthIfValid(Node x, boolean isRoot, long lo, long hi) {
        if (x.n > 2 * t - 1 || (!isRoot && x.n < t - 1)) return -1;
        for (int i = 0; i < x.n; i++) {
            if (x.keys[i] <= lo || x.keys[i] >= hi || (i > 0 && x.keys[i - 1] >= x.keys[i])) return -1;
        }
        if (x.leaf) return 1;
        int depth = -2;
        for (int i = 0; i <= x.n; i++) {
            long l = i == 0 ? lo : x.keys[i - 1];
            long h = i == x.n ? hi : x.keys[i];
            int d = depthIfValid(x.children[i], false, l, h);
            if (d < 0 || (depth != -2 && d != depth)) return -1;
            depth = d;
        }
        return depth + 1;
    }

    public static void main(String[] args) {
        BTree small = new BTree(2);
        for (int k : new int[] {10, 20, 5, 6, 12, 30, 7, 17, 3, 4, 2, 25, 40, 1}) {
            small.add(k);
        }
        System.out.print(small.levels());
        System.out.println("height " + small.height() + ", splits " + small.splits
                + ", contains 17: " + small.contains(17) + ", contains 18: " + small.contains(18));

        // Self-check against TreeSet for a few minimum degrees
        for (int t : new int[] {2, 3, 50}) {
            Random rnd = new Random(t);
            BTree tree = new BTree(t);
            TreeSet<Integer> ref = new TreeSet<>();
            boolean ok = true;
            for (int i = 0; i < 100_000; i++) {
                int key = rnd.nextInt(1_000_000);
                ok &= tree.add(key) == ref.add(key);
                int probe = rnd.nextInt(1_000_000);
                ok &= tree.contains(probe) == ref.contains(probe);
            }
            ok &= tree.keys().equals(new ArrayList<>(ref)) && tree.valid() && tree.size == ref.size();
            System.out.printf("t=%d: %d keys, height %d, matches TreeSet and valid: %b%n",
                    t, tree.size, tree.height(), ok);
        }
    }
}

/* Output:
[10]
[4 6] [20]
[1 2 3] [5] [7] [12 17] [25 30 40]
height 3, splits 5, contains 17: true, contains 18: false
t=2: 95093 keys, height 13, matches TreeSet and valid: true
t=3: 95141 keys, height 9, matches TreeSet and valid: true
t=50: 95045 keys, height 3, matches TreeSet and valid: true
*/
