// A dynamic array (what ArrayList is) keeps its elements in a plain array that
// is larger than needed. add() writes into the spare slot; when the array is
// full it allocates one twice as big and copies everything over. A single
// add can then cost O(n), but doubling means each element is copied O(1)
// times on average, so add is amortized O(1). get/set are O(1) by index;
// insert/remove in the middle are O(n) because elements shift.
// Growing by a constant (e.g. +10) instead of a factor makes n adds O(n^2).
// Removing should null out the vacated slot so the old object can be
// garbage collected (a classic leak in hand-written stacks and lists).
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Random;

public class DynamicArray {

    static final class DynArray<E> implements Iterable<E> {
        private Object[] data = new Object[2];
        private int size;
        int copies; // how many element copies resizing has done, for the demo

        int size() { return size; }
        int capacity() { return data.length; }

        void add(E e) {
            if (size == data.length) {
                grow();
            }
            data[size++] = e;
        }

        void add(int index, E e) {
            checkPosition(index);
            if (size == data.length) {
                grow();
            }
            System.arraycopy(data, index, data, index + 1, size - index); // shift right
            data[index] = e;
            size++;
        }

        @SuppressWarnings("unchecked")
        E get(int index) {
            checkIndex(index);
            return (E) data[index];
        }

        E set(int index, E e) {
            E old = get(index);
            data[index] = e;
            return old;
        }

        E remove(int index) {
            E old = get(index);
            System.arraycopy(data, index + 1, data, index, size - index - 1); // shift left
            data[--size] = null; // let the GC reclaim the removed element
            return old;
        }

        private void grow() {
            data = Arrays.copyOf(data, data.length * 2);
            copies += size;
        }

        private void checkIndex(int index) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException("index " + index + ", size " + size);
            }
        }

        private void checkPosition(int index) {
            if (index < 0 || index > size) {
                throw new IndexOutOfBoundsException("position " + index + ", size " + size);
            }
        }

        public Iterator<E> iterator() {
            return new Iterator<>() {
                int next;
                public boolean hasNext() { return next < size; }
                public E next() {
                    if (next >= size) {
                        throw new NoSuchElementException();
                    }
                    return get(next++);
                }
            };
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < size; i++) {
                sb.append(i == 0 ? "" : ", ").append(data[i]);
            }
            return sb.append(']').toString();
        }
    }

    public static void main(String[] args) {
        DynArray<String> a = new DynArray<>();
        for (String s : List.of("a", "b", "c", "d", "e")) {
            a.add(s);
            System.out.println("add " + s + " -> size " + a.size() + ", capacity " + a.capacity());
        }
        a.add(0, "start");
        a.set(3, "C");
        System.out.println("after add(0) and set(3): " + a);
        System.out.println("remove(1) -> " + a.remove(1) + ", now " + a);

        try {
            a.get(10);
        } catch (IndexOutOfBoundsException e) {
            System.out.println("get(10) -> IndexOutOfBoundsException");
        }

        // Amortized cost: copies per element stay below 2 however large n gets
        for (int n : new int[] {1_000, 100_000, 1_000_000}) {
            DynArray<Integer> big = new DynArray<>();
            for (int i = 0; i < n; i++) {
                big.add(i);
            }
            System.out.printf(Locale.ROOT, "n=%,d capacity=%,d copies/element=%.2f%n",
                    n, big.capacity(), (double) big.copies / n);
        }

        // Self-check: the same random operations on DynArray and ArrayList
        Random rnd = new Random(7);
        DynArray<Integer> mine = new DynArray<>();
        List<Integer> ref = new ArrayList<>();
        for (int op = 0; op < 10_000; op++) {
            int value = rnd.nextInt(1000);
            switch (rnd.nextInt(4)) {
                case 0, 1 -> { mine.add(value); ref.add(value); }
                case 2 -> {
                    int pos = rnd.nextInt(ref.size() + 1);
                    mine.add(pos, value);
                    ref.add(pos, value);
                }
                default -> {
                    if (!ref.isEmpty()) {
                        int pos = rnd.nextInt(ref.size());
                        if (!mine.remove(pos).equals(ref.remove(pos))) {
                            throw new AssertionError("remove mismatch at op " + op);
                        }
                    }
                }
            }
        }
        List<Integer> copy = new ArrayList<>();
        mine.forEach(copy::add);
        System.out.println("10,000 random ops match ArrayList: " + copy.equals(ref) + " (size " + mine.size() + ")");
    }
}

/* Output:
add a -> size 1, capacity 2
add b -> size 2, capacity 2
add c -> size 3, capacity 4
add d -> size 4, capacity 4
add e -> size 5, capacity 8
after add(0) and set(3): [start, a, b, C, d, e]
remove(1) -> a, now [start, b, C, d, e]
get(10) -> IndexOutOfBoundsException
n=1,000 capacity=1,024 copies/element=1.02
n=100,000 capacity=131,072 copies/element=1.31
n=1,000,000 capacity=1,048,576 copies/element=1.05
10,000 random ops match ArrayList: true (size 5034)
*/
