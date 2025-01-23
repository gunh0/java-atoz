// Any class that implements Iterable<T> works in a for-each loop: the compiler
// turns `for (T x : source)` into source.iterator() plus hasNext()/next().
// The Iterator contract worth getting right:
//  - iterator() returns a FRESH iterator each time, so the object can be
//    looped over more than once (returning a shared one is a classic bug)
//  - hasNext() has no side effects and may be called any number of times
//  - next() past the end throws NoSuchElementException
//  - remove() is optional; the default implementation throws
//    UnsupportedOperationException
//  - a fail-fast iterator remembers a modification counter and throws
//    ConcurrentModificationException when the source changed underneath it
// Iterable also brings forEach(...) and spliterator() for free, so
// StreamSupport.stream(iterable.spliterator(), false) gives a Stream.
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class CustomIterableExample {
    // 1. A half-open range with a step, computed lazily
    record Range(int from, int to, int step) implements Iterable<Integer> {
        @Override
        public Iterator<Integer> iterator() {
            return new Iterator<>() {
                private int next = from;

                @Override
                public boolean hasNext() {
                    return next < to;
                }

                @Override
                public Integer next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    int current = next;
                    next += step;
                    return current;
                }
            };
        }
    }

    // 2. A binary search tree whose iterator walks in order with an explicit stack
    static final class Tree implements Iterable<String> {
        private record Node(String value, Node left, Node right) {}

        private Node root;
        private int modCount;

        void add(String value) {
            root = insert(root, value);
            modCount++;
        }

        private static Node insert(Node n, String v) {
            if (n == null) {
                return new Node(v, null, null);
            }
            int c = v.compareTo(n.value);
            if (c < 0) {
                return new Node(n.value, insert(n.left, v), n.right);
            }
            if (c > 0) {
                return new Node(n.value, n.left, insert(n.right, v));
            }
            return n;
        }

        @Override
        public Iterator<String> iterator() {
            return new Iterator<>() {
                private final Deque<Node> stack = new ArrayDeque<>();
                private final int expectedModCount = modCount;

                { pushLeft(root); }

                private void pushLeft(Node n) {
                    for (; n != null; n = n.left) {
                        stack.push(n);
                    }
                }

                @Override
                public boolean hasNext() {
                    return !stack.isEmpty();
                }

                @Override
                public String next() {
                    if (modCount != expectedModCount) {
                        throw new ConcurrentModificationException();
                    }
                    if (stack.isEmpty()) {
                        throw new NoSuchElementException();
                    }
                    Node n = stack.pop();
                    pushLeft(n.right);
                    return n.value;
                }
            };
        }
    }

    // 3. The bug: an Iterable that hands out the same iterator every time
    static final class OneShot implements Iterable<String> {
        private final Iterator<String> shared = List.of("x", "y").iterator();

        @Override
        public Iterator<String> iterator() {
            return shared;
        }
    }

    public static void main(String[] args) {
        Range evens = new Range(0, 10, 2);
        StringBuilder out = new StringBuilder();
        for (int n : evens) {
            out.append(n).append(' ');
        }
        System.out.println("for-each over Range:  " + out.toString().strip());
        System.out.println("second loop works:    " + StreamSupport.stream(evens.spliterator(), false).toList());

        Iterator<Integer> it = new Range(0, 2, 1).iterator();
        System.out.println("hasNext x3: " + it.hasNext() + " " + it.hasNext() + " " + it.hasNext() + ", next: " + it.next() + ", " + it.next());
        try {
            it.next();
        } catch (NoSuchElementException e) {
            System.out.println("next() past the end: NoSuchElementException");
        }
        try {
            evens.iterator().remove();
        } catch (UnsupportedOperationException e) {
            System.out.println("default remove():    UnsupportedOperationException");
        }

        Tree tree = new Tree();
        for (String s : List.of("mango", "apple", "pear", "banana", "cherry", "apple")) {
            tree.add(s);
        }
        List<String> inOrder = new ArrayList<>();
        tree.forEach(inOrder::add); // Iterable.forEach is a default method
        System.out.println("tree in order: " + inOrder);
        System.out.println("joined via stream: "
                + StreamSupport.stream(tree.spliterator(), false).map(String::toUpperCase).collect(Collectors.joining(",")));
        try {
            for (String s : tree) {
                if (s.equals("banana")) {
                    tree.add("blueberry"); // structural change during iteration
                }
            }
        } catch (ConcurrentModificationException e) {
            System.out.println("adding during for-each: ConcurrentModificationException");
        }

        OneShot oneShot = new OneShot();
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        oneShot.forEach(first::add);
        oneShot.forEach(second::add);
        System.out.println("shared iterator, first loop " + first + ", second loop " + second);
    }
}

/* Output:
for-each over Range:  0 2 4 6 8
second loop works:    [0, 2, 4, 6, 8]
hasNext x3: true true true, next: 0, 1
next() past the end: NoSuchElementException
default remove():    UnsupportedOperationException
tree in order: [apple, banana, cherry, mango, pear]
joined via stream: APPLE,BANANA,CHERRY,MANGO,PEAR
adding during for-each: ConcurrentModificationException
shared iterator, first loop [x, y], second loop []
*/
