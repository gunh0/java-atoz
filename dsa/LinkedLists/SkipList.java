// A skip list is a sorted linked list with express lanes. Level 0 links every
// node; each node is also promoted to level 1 with probability 1/2, to level 2
// with probability 1/4, and so on. A search starts at the top level of a
// sentinel head, runs right while the next key is smaller, and drops a level
// when it would overshoot; on average it looks at about 2 nodes per level and
// there are about log2(n) levels, so search, insert and delete take expected
// O(log n) time with O(n) expected space. Unlike a balanced tree there are no
// rotations: an insert splices the new node into each of its levels using the
// "update" array of predecessors found on the way down. Randomness makes the
// balance probabilistic rather than guaranteed; java.util.concurrent's
// ConcurrentSkipListMap uses the same structure because splicing is local.
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

public class SkipList {
    static final int MAX_LEVEL = 24;

    static final class Node {
        final int key;
        final Node[] next;
        Node(int key, int levels) {
            this.key = key;
            this.next = new Node[levels];
        }
    }

    private final Node head = new Node(Integer.MIN_VALUE, MAX_LEVEL);
    private final Random random;
    private int level = 1; // levels currently in use
    private int size;
    long steps;            // nodes visited, to show the O(log n) search cost

    SkipList(long seed) {
        this.random = new Random(seed);
    }

    private int randomLevel() {
        int lvl = 1;
        while (lvl < MAX_LEVEL && random.nextBoolean()) {
            lvl++;
        }
        return lvl;
    }

    // Fill update[i] with the last node before key on level i; return the level-0 candidate
    private Node findPredecessors(int key, Node[] update) {
        Node x = head;
        for (int i = level - 1; i >= 0; i--) {
            while (x.next[i] != null && x.next[i].key < key) {
                x = x.next[i];
                steps++;
            }
            if (update != null) {
                update[i] = x;
            }
        }
        return x.next[0];
    }

    boolean contains(int key) {
        Node x = findPredecessors(key, null);
        return x != null && x.key == key;
    }

    boolean add(int key) {
        Node[] update = new Node[MAX_LEVEL];
        Node x = findPredecessors(key, update);
        if (x != null && x.key == key) {
            return false;
        }
        int lvl = randomLevel();
        if (lvl > level) {
            for (int i = level; i < lvl; i++) {
                update[i] = head;
            }
            level = lvl;
        }
        Node n = new Node(key, lvl);
        for (int i = 0; i < lvl; i++) { // splice into every level the node lives on
            n.next[i] = update[i].next[i];
            update[i].next[i] = n;
        }
        size++;
        return true;
    }

    boolean remove(int key) {
        Node[] update = new Node[MAX_LEVEL];
        Node x = findPredecessors(key, update);
        if (x == null || x.key != key) {
            return false;
        }
        for (int i = 0; i < x.next.length; i++) {
            update[i].next[i] = x.next[i];
        }
        while (level > 1 && head.next[level - 1] == null) {
            level--;
        }
        size--;
        return true;
    }

    // Smallest key >= key, or null
    Integer ceiling(int key) {
        Node x = findPredecessors(key, null);
        return x == null ? null : x.key;
    }

    List<Integer> toList() {
        List<Integer> out = new ArrayList<>();
        for (Node x = head.next[0]; x != null; x = x.next[0]) {
            out.add(x.key);
        }
        return out;
    }

    String levels() {
        StringBuilder sb = new StringBuilder();
        for (int i = level - 1; i >= 0; i--) {
            sb.append("L").append(i).append(":");
            for (Node x = head.next[0]; x != null; x = x.next[0]) {
                sb.append(x.next.length > i ? String.format(" %2d", x.key) : "   ");
            }
            sb.setLength(sb.toString().stripTrailing().length());
            sb.append('\n');
        }
        return sb.toString();
    }

    int size() { return size; }
    int level() { return level; }

    public static void main(String[] args) {
        SkipList small = new SkipList(7);
        for (int k : new int[] {30, 10, 50, 20, 60, 40, 70, 80, 90}) {
            small.add(k);
        }
        System.out.print(small.levels());
        System.out.println("contains 40: " + small.contains(40) + ", contains 45: " + small.contains(45)
                + ", ceiling(45): " + small.ceiling(45));
        small.remove(40);
        System.out.println("after remove(40): " + small.toList());

        // Self-check against TreeSet with a random mix of operations
        Random rnd = new Random(42);
        SkipList list = new SkipList(1);
        TreeSet<Integer> ref = new TreeSet<>();
        boolean ok = true;
        for (int i = 0; i < 200_000; i++) {
            int key = rnd.nextInt(50_000);
            switch (rnd.nextInt(3)) {
                case 0 -> ok &= list.add(key) == ref.add(key);
                case 1 -> ok &= list.remove(key) == ref.remove(key);
                default -> ok &= list.contains(key) == ref.contains(key)
                        && java.util.Objects.equals(list.ceiling(key), ref.ceiling(key));
            }
        }
        ok &= list.toList().equals(new ArrayList<>(ref)) && list.size() == ref.size();
        System.out.println("200000 random ops match TreeSet: " + ok + " (size " + list.size() + ")");

        // Search cost grows with log n
        for (int n : new int[] {1_000, 100_000}) {
            SkipList s = new SkipList(3);
            for (int i = 0; i < n; i++) {
                s.add(i);
            }
            s.steps = 0;
            for (int i = 0; i < 1_000; i++) {
                s.contains(i * (n / 1_000));
            }
            System.out.printf(java.util.Locale.ROOT, "n=%,d: %d levels, %.1f nodes visited per search (log2 n = %.1f)%n",
                    n, s.level(), s.steps / 1_000.0, Math.log(n) / Math.log(2));
        }
    }
}

/* Output:
L4:    20
L3:    20 30
L2:    20 30          70 80
L1:    20 30       60 70 80 90
L0: 10 20 30 40 50 60 70 80 90
contains 40: true, contains 45: false, ceiling(45): 50
after remove(40): [10, 20, 30, 50, 60, 70, 80, 90]
200000 random ops match TreeSet: true (size 23268)
n=1,000: 8 levels, 9.7 nodes visited per search (log2 n = 10.0)
n=100,000: 16 levels, 15.4 nodes visited per search (log2 n = 16.6)
*/
