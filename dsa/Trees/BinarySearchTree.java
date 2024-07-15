// A binary search tree keeps every key in the left subtree smaller and every
// key in the right subtree larger than the node's key, so search, insert
// and delete follow a single root-to-leaf path: O(h) for height h. An
// in-order traversal (left, node, right) visits the keys in sorted order.
// Deletion has three cases: a leaf is simply removed, a node with one child
// is replaced by that child, and a node with two children takes the key of
// its in-order successor (the minimum of its right subtree), which is then
// deleted from there. The catch: h is O(log n) only for random insertion
// orders. Sorted input degenerates the tree into a linked list (h = n),
// which is why real libraries use self-balancing trees (AVL, red-black).
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

public class BinarySearchTree {

    static final class Bst {
        private static final class Node {
            int key;
            Node left, right;
            Node(int key) { this.key = key; }
        }

        private Node root;
        private int size;

        int size() { return size; }

        boolean contains(int key) {
            Node n = root;
            while (n != null) {
                if (key == n.key) {
                    return true;
                }
                n = key < n.key ? n.left : n.right;
            }
            return false;
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
            } // equal: already present, sets hold no duplicates
            return n;
        }

        boolean delete(int key) {
            int before = size;
            root = delete(root, key);
            return size < before;
        }

        private Node delete(Node n, int key) {
            if (n == null) {
                return null; // not found
            }
            if (key < n.key) {
                n.left = delete(n.left, key);
            } else if (key > n.key) {
                n.right = delete(n.right, key);
            } else if (n.left == null) {
                size--;
                return n.right;          // leaf or only a right child
            } else if (n.right == null) {
                size--;
                return n.left;           // only a left child
            } else {
                Node succ = n.right;     // two children: in-order successor
                while (succ.left != null) {
                    succ = succ.left;
                }
                n.key = succ.key;
                n.right = delete(n.right, succ.key);
            }
            return n;
        }

        int height() { return height(root); }

        private int height(Node n) {
            return n == null ? 0 : 1 + Math.max(height(n.left), height(n.right));
        }

        List<Integer> inOrder() {
            List<Integer> out = new ArrayList<>();
            inOrder(root, out);
            return out;
        }

        private void inOrder(Node n, List<Integer> out) {
            if (n != null) {
                inOrder(n.left, out);
                out.add(n.key);
                inOrder(n.right, out);
            }
        }

        // Sideways drawing: right subtree on top, root at the left margin
        String draw() {
            StringBuilder sb = new StringBuilder();
            draw(root, 0, sb);
            return sb.toString();
        }

        private void draw(Node n, int depth, StringBuilder sb) {
            if (n != null) {
                draw(n.right, depth + 1, sb);
                sb.append("    ".repeat(depth)).append(n.key).append('\n');
                draw(n.left, depth + 1, sb);
            }
        }
    }

    public static void main(String[] args) {
        Bst t = new Bst();
        for (int k : new int[] {50, 30, 70, 20, 40, 60, 80, 65}) {
            t.insert(k);
        }
        System.out.print(t.draw());
        System.out.println("in-order: " + t.inOrder() + ", height " + t.height());
        System.out.println("contains 65: " + t.contains(65) + ", contains 66: " + t.contains(66));

        t.delete(20); // leaf
        t.delete(60); // one child (65)
        t.delete(50); // two children: root takes successor 65
        System.out.println("after deleting 20, 60, 50: " + t.inOrder());
        System.out.print(t.draw());

        // Insertion order decides the shape
        Bst sorted = new Bst();
        Bst shuffled = new Bst();
        List<Integer> keys = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            sorted.insert(i);
            keys.add(i);
        }
        java.util.Collections.shuffle(keys, new Random(1));
        keys.forEach(shuffled::insert);
        System.out.println("height for 1,000 keys: sorted inserts " + sorted.height()
                + ", shuffled inserts " + shuffled.height());

        // Self-check against TreeSet
        Random rnd = new Random(99);
        Bst mine = new Bst();
        TreeSet<Integer> ref = new TreeSet<>();
        boolean same = true;
        for (int op = 0; op < 20_000; op++) {
            int k = rnd.nextInt(2_000);
            switch (rnd.nextInt(3)) {
                case 0 -> same &= mine.insert(k) == ref.add(k);
                case 1 -> same &= mine.delete(k) == ref.remove(k);
                default -> same &= mine.contains(k) == ref.contains(k);
            }
        }
        same &= mine.inOrder().equals(new ArrayList<>(ref)) && mine.size() == ref.size();
        System.out.println("20,000 random ops match TreeSet: " + same + " (size " + mine.size() + ")");
    }
}

/* Output:
        80
    70
            65
        60
50
        40
    30
        20
in-order: [20, 30, 40, 50, 60, 65, 70, 80], height 4
contains 65: true, contains 66: false
after deleting 20, 60, 50: [30, 40, 65, 70, 80]
        80
    70
65
        40
    30
height for 1,000 keys: sorted inserts 1000, shuffled inserts 21
20,000 random ops match TreeSet: true (size 1007)
*/
