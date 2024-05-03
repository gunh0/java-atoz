// A doubly linked list gives every node a prev and a next pointer, so given
// a node you can unlink it in O(1) without searching for its predecessor,
// and you can walk the list in both directions. This is what makes it the
// backbone of LRU caches and of java.util.LinkedList.
// A single sentinel node (a dummy that is both "before the first" and
// "after the last") turns the list into a ring and removes every null check:
// an empty list is the sentinel pointing at itself, and insert/unlink are
// the same four pointer writes everywhere, at the ends or in the middle.
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

public class DoublyLinkedList {

    static final class DList<E> implements Iterable<E> {
        static final class Node<E> {
            E value;
            Node<E> prev;
            Node<E> next;
            Node(E value) { this.value = value; }
        }

        private final Node<E> sentinel = new Node<>(null);
        private int size;

        DList() {
            sentinel.prev = sentinel;
            sentinel.next = sentinel;
        }

        int size() { return size; }
        boolean isEmpty() { return size == 0; }

        // Insert a new node between `before` and before.next
        private Node<E> insertAfter(Node<E> before, E e) {
            Node<E> node = new Node<>(e);
            node.prev = before;
            node.next = before.next;
            before.next.prev = node;
            before.next = node;
            size++;
            return node;
        }

        Node<E> addFirst(E e) { return insertAfter(sentinel, e); }
        Node<E> addLast(E e) { return insertAfter(sentinel.prev, e); }

        // O(1): the node knows both neighbours
        E unlink(Node<E> node) {
            node.prev.next = node.next;
            node.next.prev = node.prev;
            node.prev = node.next = null; // a detached node cannot corrupt the list later
            size--;
            return node.value;
        }

        E removeFirst() {
            if (isEmpty()) {
                throw new NoSuchElementException("empty list");
            }
            return unlink(sentinel.next);
        }

        E removeLast() {
            if (isEmpty()) {
                throw new NoSuchElementException("empty list");
            }
            return unlink(sentinel.prev);
        }

        public Iterator<E> iterator() {
            return new Iterator<>() {
                Node<E> cur = sentinel.next;
                public boolean hasNext() { return cur != sentinel; }
                public E next() {
                    if (cur == sentinel) {
                        throw new NoSuchElementException();
                    }
                    E v = cur.value;
                    cur = cur.next;
                    return v;
                }
            };
        }

        List<E> backwards() {
            List<E> out = new ArrayList<>();
            for (Node<E> n = sentinel.prev; n != sentinel; n = n.prev) {
                out.add(n.value);
            }
            return out;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("[");
            for (E e : this) {
                sb.append(sb.length() > 1 ? " <-> " : "").append(e);
            }
            return sb.append(']').toString();
        }
    }

    public static void main(String[] args) {
        DList<String> list = new DList<>();
        list.addLast("b");
        DList.Node<String> c = list.addLast("c");
        list.addLast("d");
        list.addFirst("a");
        System.out.println("forward:   " + list);
        System.out.println("backward:  " + list.backwards());

        // Removing a node we hold a reference to: no search needed
        System.out.println("unlink(c): " + list.unlink(c) + " -> " + list);
        System.out.println("removeFirst " + list.removeFirst() + ", removeLast " + list.removeLast() + " -> " + list);
        list.removeFirst();
        System.out.println("empty again: " + list.isEmpty() + " " + list);

        try {
            list.removeLast();
        } catch (NoSuchElementException e) {
            System.out.println("removeLast on empty -> NoSuchElementException");
        }

        // Self-check as a deque against ArrayDeque
        Random rnd = new Random(3);
        DList<Integer> mine = new DList<>();
        Deque<Integer> ref = new ArrayDeque<>();
        for (int op = 0; op < 10_000; op++) {
            int v = rnd.nextInt(100);
            switch (rnd.nextInt(4)) {
                case 0 -> { mine.addFirst(v); ref.addFirst(v); }
                case 1 -> { mine.addLast(v); ref.addLast(v); }
                case 2 -> {
                    if (!ref.isEmpty() && !mine.removeFirst().equals(ref.removeFirst())) {
                        throw new AssertionError("removeFirst mismatch");
                    }
                }
                default -> {
                    if (!ref.isEmpty() && !mine.removeLast().equals(ref.removeLast())) {
                        throw new AssertionError("removeLast mismatch");
                    }
                }
            }
        }
        List<Integer> forward = new ArrayList<>();
        mine.forEach(forward::add);
        List<Integer> backward = new ArrayList<>();
        ref.descendingIterator().forEachRemaining(backward::add);
        System.out.println("10,000 random deque ops match ArrayDeque: "
                + (forward.equals(new ArrayList<>(ref)) && mine.backwards().equals(backward))
                + " (size " + mine.size() + ")");
    }
}

/* Output:
forward:   [a <-> b <-> c <-> d]
backward:  [d, c, b, a]
unlink(c): c -> [a <-> b <-> d]
removeFirst a, removeLast d -> [b]
empty again: true []
removeLast on empty -> NoSuchElementException
10,000 random deque ops match ArrayDeque: true (size 49)
*/
