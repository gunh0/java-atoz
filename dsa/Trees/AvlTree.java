// An AVL tree is a binary search tree that keeps itself balanced: at every
// node the heights of the two subtrees differ by at most 1. Each node stores
// its height; after an insert or delete, the nodes on the way back up are
// checked, and an imbalance of 2 is repaired with one or two rotations:
//   left-left   -> rotate right          right-right -> rotate left
//   left-right  -> rotate left, right    right-left  -> rotate right, left
// A rotation re-hangs three subtrees in O(1) and preserves the in-order
// sequence. The balance rule bounds the height by about 1.44 log2(n), so
// search, insert and delete are O(log n) even for sorted input, the case
// that turns a plain BST into a list.
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TreeSet;

public class AvlTree {

    static final class Avl {
        private static final class Node {
            int key;
            int height = 1;
            Node left, right;
            Node(int key) { this.key = key; }
        }

        private Node root;
        private int size;
        int rotations;

        int size() { return size; }
        int height() { return h(root); }

        private static int h(Node n) { return n == null ? 0 : n.height; }
        private static int balance(Node n) { return h(n.left) - h(n.right); }
        private static void update(Node n) { n.height = 1 + Math.max(h(n.left), h(n.right)); }

        //       y            x
        //      / \          / \
        //     x   C  ->    A   y
        //    / \              / \
        //   A   B            B   C
        private Node rotateRight(Node y) {
            Node x = y.left;
            y.left = x.right;
            x.right = y;
            update(y);
            update(x);
            rotations++;
            return x;
        }

        private Node rotateLeft(Node x) {
            Node y = x.right;
            x.right = y.left;
            y.left = x;
            update(x);
            update(y);
            rotations++;
            return y;
        }

        private Node rebalance(Node n) {
            update(n);
            int b = balance(n);
            if (b > 1) {                      // left heavy
                if (balance(n.left) < 0) {
                    n.left = rotateLeft(n.left); // left-right case
                }
                return rotateRight(n);
            }
            if (b < -1) {                     // right heavy
                if (balance(n.right) > 0) {
                    n.right = rotateRight(n.right); // right-left case
                }
                return rotateLeft(n);
            }
            return n;
        }

        boolean contains(int key) {
            Node n = root;
            while (n != null && n.key != key) {
                n = key < n.key ? n.left : n.right;
            }
            return n != null;
        }

        boolean insert(int key) {
            int before = size;
            root = insert(root, key);
            return size > before;
        }

        private Node insert(Node n, int key) {
            if (n == null) {
                size++;
                return new Node(key);
            }
            if (key < n.key) {
                n.left = insert(n.left, key);
            } else if (key > n.key) {
                n.right = insert(n.right, key);
            } else {
                return n;
            }
            return rebalance(n);
        }

        boolean delete(int key) {
            int before = size;
            root = delete(root, key);
            return size < before;
        }

        private Node delete(Node n, int key) {
            if (n == null) {
                return null;
            }
            if (key < n.key) {
                n.left = delete(n.left, key);
            } else if (key > n.key) {
                n.right = delete(n.right, key);
            } else if (n.left == null || n.right == null) {
                size--;
                return n.left != null ? n.left : n.right;
            } else {
                Node succ = n.right;
                while (succ.left != null) {
                    succ = succ.left;
                }
                n.key = succ.key;
                n.right = delete(n.right, succ.key);
            }
            return rebalance(n);
        }

        // Verifies ordering, stored heights and the AVL balance rule everywhere
        boolean isValid() {
            return check(root, Long.MIN_VALUE, Long.MAX_VALUE) >= 0;
        }

        private int check(Node n, long min, long max) {
            if (n == null) {
                return 0;
            }
            if (n.key <= min || n.key >= max) {
                return -1;
            }
            int l = check(n.left, min, n.key), r = check(n.right, n.key, max);
            if (l < 0 || r < 0 || Math.abs(l - r) > 1 || n.height != 1 + Math.max(l, r)) {
                return -1;
            }
            return n.height;
        }

        List<Integer> inOrder() {
            List<Integer> out = new ArrayList<>();
            walk(root, out);
            return out;
        }

        private void walk(Node n, List<Integer> out) {
            if (n != null) {
                walk(n.left, out);
                out.add(n.key);
                walk(n.right, out);
            }
        }

        String shape() { return shape(root); }

        private String shape(Node n) {
            if (n == null) {
                return "-";
            }
            if (n.left == null && n.right == null) {
                return String.valueOf(n.key);
            }
            return n.key + "(" + shape(n.left) + " " + shape(n.right) + ")";
        }
    }

    public static void main(String[] args) {
        // The four rotation cases, each triggered by three inserts
        int[][] cases = {{3, 2, 1}, {1, 2, 3}, {3, 1, 2}, {1, 3, 2}};
        String[] names = {"left-left  ", "right-right", "left-right ", "right-left "};
        for (int i = 0; i < cases.length; i++) {
            Avl t = new Avl();
            for (int k : cases[i]) {
                t.insert(k);
            }
            System.out.println(names[i] + " insert " + java.util.Arrays.toString(cases[i])
                    + " -> " + t.shape() + " after " + t.rotations + " rotation(s)");
        }

        // Sorted input: a plain BST would reach height 100,000
        Avl sorted = new Avl();
        for (int k = 1; k <= 100_000; k++) {
            sorted.insert(k);
        }
        System.out.printf(Locale.ROOT, "100,000 sorted inserts: height %d (1.44*log2(n) = %.1f), valid %b%n",
                sorted.height(), 1.44 * Math.log(100_000) / Math.log(2), sorted.isValid());

        // Self-check against TreeSet, validating the AVL invariant along the way
        Random rnd = new Random(31);
        Avl mine = new Avl();
        TreeSet<Integer> ref = new TreeSet<>();
        boolean same = true;
        for (int op = 0; op < 50_000; op++) {
            int k = rnd.nextInt(5_000);
            switch (rnd.nextInt(3)) {
                case 0 -> same &= mine.insert(k) == ref.add(k);
                case 1 -> same &= mine.delete(k) == ref.remove(k);
                default -> same &= mine.contains(k) == ref.contains(k);
            }
            if (op % 1_000 == 0) {
                same &= mine.isValid();
            }
        }
        same &= mine.isValid() && mine.inOrder().equals(new ArrayList<>(ref));
        System.out.println("50,000 random ops match TreeSet and stay balanced: " + same
                + " (size " + mine.size() + ", height " + mine.height() + ")");
    }
}

/* Output:
left-left   insert [3, 2, 1] -> 2(1 3) after 1 rotation(s)
right-right insert [1, 2, 3] -> 2(1 3) after 1 rotation(s)
left-right  insert [3, 1, 2] -> 2(1 3) after 2 rotation(s)
right-left  insert [1, 3, 2] -> 2(1 3) after 2 rotation(s)
100,000 sorted inserts: height 17 (1.44*log2(n) = 23.9), valid true
50,000 random ops match TreeSet and stay balanced: true (size 2480, height 14)
*/
