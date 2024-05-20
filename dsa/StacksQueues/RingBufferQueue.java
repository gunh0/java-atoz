// A queue is first-in, first-out. Dequeuing from the front of a plain array
// would shift every element (O(n)); a ring buffer avoids that by letting
// head and tail wrap around: index = (head + i) % capacity. Both offer and
// poll are O(1), and the array is reused forever without shifting.
// When the buffer is full it is grown by copying the elements out in queue
// order (unwrapping the ring) into an array twice as large. Keeping an
// explicit size distinguishes "full" from "empty", which both look like
// head == tail. ArrayDeque is exactly this, with power-of-two capacities.
import java.util.ArrayDeque;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Random;

public class RingBufferQueue {

    static final class Ring<E> {
        private Object[] buf;
        private int head; // index of the oldest element
        private int size;

        Ring(int capacity) { buf = new Object[capacity]; }

        int size() { return size; }
        int capacity() { return buf.length; }
        boolean isEmpty() { return size == 0; }

        void offer(E e) {
            if (size == buf.length) {
                grow();
            }
            buf[(head + size) % buf.length] = e; // tail slot, wrapping around
            size++;
        }

        @SuppressWarnings("unchecked")
        E poll() {
            if (size == 0) {
                throw new NoSuchElementException("empty queue");
            }
            E e = (E) buf[head];
            buf[head] = null;
            head = (head + 1) % buf.length;
            size--;
            return e;
        }

        @SuppressWarnings("unchecked")
        E peek() {
            if (size == 0) {
                throw new NoSuchElementException("empty queue");
            }
            return (E) buf[head];
        }

        private void grow() {
            Object[] bigger = new Object[buf.length * 2];
            for (int i = 0; i < size; i++) {
                bigger[i] = buf[(head + i) % buf.length]; // unwrap into queue order
            }
            buf = bigger;
            head = 0;
        }

        // Physical layout of the backing array, to see the wrap-around
        String layout() {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < buf.length; i++) {
                sb.append(i == 0 ? "" : " ").append(buf[i] == null ? "." : buf[i]);
            }
            return sb.append("] head=").append(head).append(" size=").append(size).toString();
        }
    }

    public static void main(String[] args) {
        Ring<String> q = new Ring<>(4);
        q.offer("A");
        q.offer("B");
        q.offer("C");
        System.out.println("offer A B C    " + q.layout());
        System.out.println("poll " + q.poll() + ", poll " + q.poll() + "   " + q.layout());
        q.offer("D");
        q.offer("E"); // wraps to index 0
        q.offer("F");
        System.out.println("offer D E F    " + q.layout());
        q.offer("G"); // full: grow and unwrap
        System.out.println("offer G (grow) " + q.layout());
        StringBuilder order = new StringBuilder();
        while (!q.isEmpty()) {
            order.append(q.poll());
        }
        System.out.println("poll order: " + order);
        try {
            q.peek();
        } catch (NoSuchElementException e) {
            System.out.println("peek on empty -> NoSuchElementException");
        }

        // Self-check against ArrayDeque used as a queue
        Random rnd = new Random(17);
        Ring<Integer> mine = new Ring<>(2);
        Queue<Integer> ref = new ArrayDeque<>();
        boolean same = true;
        for (int op = 0; op < 20_000; op++) {
            if (ref.isEmpty() || rnd.nextInt(5) < 3) {
                int v = rnd.nextInt();
                mine.offer(v);
                ref.offer(v);
            } else {
                same &= mine.poll().equals(ref.poll());
            }
        }
        while (!ref.isEmpty()) {
            same &= mine.poll().equals(ref.poll());
        }
        System.out.println("20,000 random offer/poll match ArrayDeque: " + same
                + " (final capacity " + mine.capacity() + ")");
    }
}

/* Output:
offer A B C    [A B C .] head=0 size=3
poll A, poll B   [. . C .] head=2 size=1
offer D E F    [E F C D] head=2 size=4
offer G (grow) [C D E F G . . .] head=0 size=5
poll order: CDEFG
peek on empty -> NoSuchElementException
20,000 random offer/poll match ArrayDeque: true (final capacity 4096)
*/
