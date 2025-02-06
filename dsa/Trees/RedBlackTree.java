// A red-black tree is a binary search tree whose nodes are colored so that
//   1. a red node never has a red child, and
//   2. every path from the root down to a null link has the same number of
//      black nodes (the black height).
// Together these force the longest path to be at most twice the shortest, so
// the height is at most 2 log2(n + 1) and every operation is O(log n).
// This is the left-leaning variant (Sedgewick): red links may only lean left,
// which makes it a binary encoding of a 2-3 tree (a red link glues two keys
// into one 3-node). Insert adds a red leaf and fixes things on the way back up
// with three local moves: rotate left a right-leaning red link, rotate right
// two reds in a row, and flip colors when both children are red (splitting a
// temporary 4-node). Delete pushes a red link down the search path (moveRedLeft
// / moveRedRight) so the node removed at the bottom is never a lone black one.
// TreeMap is a classic (not left-leaning) red-black tree with the same bounds.
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

public class RedBlackTree {

    static final class LlrbMap {
        private static final boolean RED = true;
        private static final boolean BLACK = false;

        private static final class Node {
            int key;
            String value;
            Node left, right;
            boolean color = RED; // new nodes join their parent's 2-3 node
            int size = 1;
            Node(int key, String value) { this.key = key; this.value = value; }
        }

        private Node root;

        private static boolean isRed(Node n) { return n != null && n.color == RED; }
        private static int size(Node n) { return n == null ? 0 : n.size; }
        int size() { return size(root); }

        String get(int key) {
            Node x = root;
            while (x != null) {
                if (key < x.key) x = x.left;
                else if (key > x.key) x = x.right;
                else return x.value;
            }
            return null;
        }

        void put(int key, String value) {
            root = put(root, key, value);
            root.color = BLACK;
        }

        private Node put(Node h, int key, String value) {
            if (h == null) return new Node(key, value);
            if (key < h.key) h.left = put(h.left, key, value);
            else if (key > h.key) h.right = put(h.right, key, value);
            else h.value = value;
            return balance(h);
        }

        //    h              x
        //   / \            / \
        //  a   x    ->    h   c      (x was a red right child; now it leans left)
        //     / \        / \
        //    b   c      a   b
        private Node rotateLeft(Node h) {
            Node x = h.right;
            h.right = x.left;
            x.left = h;
            x.color = h.color;
            h.color = RED;
            x.size = h.size;
            h.size = 1 + size(h.left) + size(h.right);
            return x;
        }

        private Node rotateRight(Node h) {
            Node x = h.left;
            h.left = x.right;
            x.right = h;
            x.color = h.color;
            h.color = RED;
            x.size = h.size;
            h.size = 1 + size(h.left) + size(h.right);
            return x;
        }

        private void flipColors(Node h) {
            h.color = !h.color;
            h.left.color = !h.left.color;
            h.right.color = !h.right.color;
        }

        // The three fix-ups, applied bottom-up after insert and delete
        private Node balance(Node h) {
            if (isRed(h.right) && !isRed(h.left)) h = rotateLeft(h);
            if (isRed(h.left) && isRed(h.left.left)) h = rotateRight(h);
            if (isRed(h.left) && isRed(h.right)) flipColors(h);
            h.size = 1 + size(h.left) + size(h.right);
            return h;
        }

        void remove(int key) {
            if (get(key) == null) return;
            if (!isRed(root.left) && !isRed(root.right)) root.color = RED;
            root = remove(root, key);
            if (root != null) root.color = BLACK;
        }

        // Make h.left or one of its children red, borrowing from the right sibling
        private Node moveRedLeft(Node h) {
            flipColors(h);
            if (isRed(h.right.left)) {
                h.right = rotateRight(h.right);
                h = rotateLeft(h);
                flipColors(h);
            }
            return h;
        }

        private Node moveRedRight(Node h) {
            flipColors(h);
            if (isRed(h.left.left)) {
                h = rotateRight(h);
                flipColors(h);
            }
            return h;
        }

        private Node removeMin(Node h) {
            if (h.left == null) return null;
            if (!isRed(h.left) && !isRed(h.left.left)) h = moveRedLeft(h);
            h.left = removeMin(h.left);
            return balance(h);
        }

        private Node remove(Node h, int key) {
            if (key < h.key) {
                if (!isRed(h.left) && !isRed(h.left.left)) h = moveRedLeft(h);
                h.left = remove(h.left, key);
            } else {
                if (isRed(h.left)) h = rotateRight(h);
                if (key == h.key && h.right == null) return null;
                if (!isRed(h.right) && !isRed(h.right.left)) h = moveRedRight(h);
                if (key == h.key) {
                    Node min = h.right; // replace with the successor, then delete it
                    while (min.left != null) min = min.left;
                    h.key = min.key;
                    h.value = min.value;
                    h.right = removeMin(h.right);
                } else {
                    h.right = remove(h.right, key);
                }
            }
            return balance(h);
        }

        int height() { return height(root); }
        private int height(Node n) { return n == null ? 0 : 1 + Math.max(height(n.left), height(n.right)); }

        List<Integer> keys() {
            List<Integer> out = new ArrayList<>();
            inOrder(root, out);
            return out;
        }

        private void inOrder(Node n, List<Integer> out) {
            if (n == null) return;
            inOrder(n.left, out);
            out.add(n.key);
            inOrder(n.right, out);
        }

        // Returns the black height, or throws if an invariant is broken
        int check() {
            if (isRed(root)) throw new IllegalStateException("red root");
            return check(root, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }

        private int check(Node n, long lo, long hi) {
            if (n == null) return 0;
            if (n.key <= lo || n.key >= hi) throw new IllegalStateException("BST order broken at " + n.key);
            if (isRed(n.right)) throw new IllegalStateException("right-leaning red at " + n.key);
            if (isRed(n) && isRed(n.left)) throw new IllegalStateException("red-red at " + n.key);
            if (n.size != 1 + size(n.left) + size(n.right)) throw new IllegalStateException("size at " + n.key);
            int l = check(n.left, lo, n.key);
            int r = check(n.right, n.key, hi);
            if (l != r) throw new IllegalStateException("black height differs at " + n.key);
            return l + (isRed(n) ? 0 : 1);
        }

        String shape() { return shape(root); }
        private String shape(Node n) {
            if (n == null) return ".";
            String k = (isRed(n) ? "r" : "") + n.key;
            return n.left == null && n.right == null ? k : "(" + shape(n.left) + " " + k + " " + shape(n.right) + ")";
        }
    }

    public static void main(String[] args) {
        LlrbMap t = new LlrbMap();
        for (int k = 1; k <= 10; k++) {
            t.put(k, "v" + k);
        }
        System.out.println("1..10 inserted: " + t.shape());
        System.out.println("black height " + t.check() + ", height " + t.height());
        t.remove(4);
        t.remove(8);
        System.out.println("removed 4, 8:   " + t.shape());
        System.out.println("get(7) = " + t.get(7) + ", get(8) = " + t.get(8) + ", black height " + t.check());

        // Sorted input, the worst case for a plain BST (height n)
        LlrbMap sorted = new LlrbMap();
        int n = 1 << 20;
        for (int k = 0; k < n; k++) {
            sorted.put(k, "");
        }
        System.out.printf("%d sorted keys: height %d (log2 n = 20, bound 2 log2(n+1) = 40)%n",
                n, sorted.height());

        // Self-check against TreeMap, verifying the invariants as we go
        Random rnd = new Random(11);
        LlrbMap map = new LlrbMap();
        TreeMap<Integer, String> ref = new TreeMap<>();
        boolean ok = true;
        for (int i = 0; i < 100_000; i++) {
            int key = rnd.nextInt(5_000);
            if (rnd.nextInt(3) == 0) {
                map.remove(key);
                ref.remove(key);
            } else {
                map.put(key, "v" + i);
                ref.put(key, "v" + i);
            }
            if (i % 1_000 == 0) {
                map.check(); // throws on any violation
            }
            ok &= map.get(key) == null ? !ref.containsKey(key) : map.get(key).equals(ref.get(key));
        }
        map.check();
        ok &= map.keys().equals(new ArrayList<>(ref.keySet())) && map.size() == ref.size();
        for (Map.Entry<Integer, String> e : ref.entrySet()) {
            ok &= e.getValue().equals(map.get(e.getKey()));
        }
        System.out.println("100000 random puts/removes match TreeMap: " + ok + ", invariants hold, size " + map.size());
    }
}

/* Output:
1..10 inserted: ((1 2 3) 4 ((5 r6 7) 8 (r9 10 .)))
black height 3, height 4
removed 4, 8:   ((1 2 3) 5 ((r6 7 .) 9 10))
get(7) = v7, get(8) = null, black height 3
1048576 sorted keys: height 21 (log2 n = 20, bound 2 log2(n+1) = 40)
100000 random puts/removes match TreeMap: true, invariants hold, size 3343
*/
