// A singly linked list is a chain of nodes, each holding a value and a
// pointer to the next node. Keeping both head and tail pointers makes
// addFirst, addLast and removeFirst O(1); anything by index or by value is
// O(n) because the list must be walked from the head. Removing a node needs
// its predecessor, which a singly linked list can only find by walking.
// In-place reversal is the classic exercise: walk once, flipping each next
// pointer, keeping track of the previous node. Floyd's tortoise-and-hare
// finds the middle (and detects cycles) with two pointers in one pass.
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

public class SinglyLinkedList {

    static final class SList<E> implements Iterable<E> {
        private static final class Node<E> {
            E value;
            Node<E> next;
            Node(E value, Node<E> next) { this.value = value; this.next = next; }
        }

        private Node<E> head;
        private Node<E> tail;
        private int size;

        int size() { return size; }

        void addFirst(E e) {
            head = new Node<>(e, head);
            if (tail == null) {
                tail = head;
            }
            size++;
        }

        void addLast(E e) {
            Node<E> node = new Node<>(e, null);
            if (tail == null) {
                head = tail = node;
            } else {
                tail.next = node;
                tail = node;
            }
            size++;
        }

        E removeFirst() {
            if (head == null) {
                throw new NoSuchElementException("empty list");
            }
            E value = head.value;
            head = head.next;
            if (head == null) {
                tail = null;
            }
            size--;
            return value;
        }

        // Removes the first node equal to e; needs the predecessor, so it walks with prev
        boolean remove(E e) {
            Node<E> prev = null;
            for (Node<E> cur = head; cur != null; prev = cur, cur = cur.next) {
                if (cur.value.equals(e)) {
                    if (prev == null) {
                        head = cur.next;
                    } else {
                        prev.next = cur.next;
                    }
                    if (cur == tail) {
                        tail = prev;
                    }
                    size--;
                    return true;
                }
            }
            return false;
        }

        void reverse() {
            Node<E> prev = null;
            Node<E> cur = head;
            tail = head;
            while (cur != null) {
                Node<E> next = cur.next;
                cur.next = prev; // flip the pointer
                prev = cur;
                cur = next;
            }
            head = prev;
        }

        // Slow moves one step, fast two; when fast reaches the end, slow is in the middle
        E middle() {
            if (head == null) {
                throw new NoSuchElementException("empty list");
            }
            Node<E> slow = head;
            Node<E> fast = head;
            while (fast.next != null && fast.next.next != null) {
                slow = slow.next;
                fast = fast.next.next;
            }
            return slow.value;
        }

        public Iterator<E> iterator() {
            return new Iterator<>() {
                Node<E> next = head;
                public boolean hasNext() { return next != null; }
                public E next() {
                    if (next == null) {
                        throw new NoSuchElementException();
                    }
                    E value = next.value;
                    next = next.next;
                    return value;
                }
            };
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Node<E> n = head; n != null; n = n.next) {
                sb.append(n.value).append(" -> ");
            }
            return sb.append("null").toString();
        }
    }

    public static void main(String[] args) {
        SList<Integer> list = new SList<>();
        for (int i = 1; i <= 5; i++) {
            list.addLast(i);
        }
        list.addFirst(0);
        System.out.println("list:        " + list);
        System.out.println("middle:      " + list.middle());
        list.reverse();
        System.out.println("reversed:    " + list);
        System.out.println("remove(3):   " + list.remove(3) + " -> " + list);
        System.out.println("remove(42):  " + list.remove(42));
        System.out.println("removeFirst: " + list.removeFirst() + " -> " + list);
        list.addLast(9); // tail must still be correct after reverse and removes
        System.out.println("addLast(9):  " + list + " (size " + list.size() + ")");

        try {
            new SList<String>().removeFirst();
        } catch (NoSuchElementException e) {
            System.out.println("removeFirst on empty -> NoSuchElementException");
        }

        // Self-check against java.util.LinkedList with random operations
        Random rnd = new Random(11);
        SList<Integer> mine = new SList<>();
        LinkedList<Integer> ref = new LinkedList<>();
        for (int op = 0; op < 5_000; op++) {
            int v = rnd.nextInt(50);
            switch (rnd.nextInt(5)) {
                case 0 -> { mine.addFirst(v); ref.addFirst(v); }
                case 1 -> { mine.addLast(v); ref.addLast(v); }
                case 2 -> {
                    if (!ref.isEmpty() && !mine.removeFirst().equals(ref.removeFirst())) {
                        throw new AssertionError("removeFirst mismatch");
                    }
                }
                case 3 -> {
                    if (mine.remove(v) != ref.remove(Integer.valueOf(v))) {
                        throw new AssertionError("remove mismatch");
                    }
                }
                default -> {
                    mine.reverse();
                    java.util.Collections.reverse(ref);
                }
            }
        }
        List<Integer> copy = new ArrayList<>();
        mine.forEach(copy::add);
        System.out.println("5,000 random ops match LinkedList: " + copy.equals(ref) + " (size " + mine.size() + ")");
    }
}

/* Output:
list:        0 -> 1 -> 2 -> 3 -> 4 -> 5 -> null
middle:      2
reversed:    5 -> 4 -> 3 -> 2 -> 1 -> 0 -> null
remove(3):   true -> 5 -> 4 -> 2 -> 1 -> 0 -> null
remove(42):  false
removeFirst: 5 -> 4 -> 2 -> 1 -> 0 -> null
addLast(9):  4 -> 2 -> 1 -> 0 -> 9 -> null (size 5)
removeFirst on empty -> NoSuchElementException
5,000 random ops match LinkedList: true (size 195)
*/
