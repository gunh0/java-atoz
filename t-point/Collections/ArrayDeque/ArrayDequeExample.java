// ArrayDeque is a resizable circular array that supports O(1) insertion and
// removal at both ends. It is the recommended implementation for both:
//  - a STACK (push/pop/peek), instead of the legacy java.util.Stack, which
//    extends Vector, synchronizes every call and exposes index-based methods
//  - a FIFO QUEUE (offer/poll/peek), usually faster than LinkedList
// Each operation comes in two flavours: addFirst/removeFirst/getFirst throw
// on failure, offerFirst/pollFirst/peekFirst return false/null instead.
// ArrayDeque does not accept null, because null is the "empty" signal of poll().
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;

public class ArrayDequeExample {
    public static void main(String[] args) {
        // Stack: check that brackets are balanced
        for (String s : new String[] {"{[()]}", "([)]", "(("}) {
            System.out.println(s + " balanced: " + balanced(s));
        }

        // Queue: breadth-first order of a small task tree
        Queue<String> queue = new ArrayDeque<>();
        queue.offer("build");
        StringBuilder order = new StringBuilder();
        while (!queue.isEmpty()) {
            String task = queue.poll();
            order.append(task).append(' ');
            if (task.equals("build")) {
                queue.offer("test");
                queue.offer("lint");
            } else if (task.equals("test")) {
                queue.offer("report");
            }
        }
        System.out.println("BFS order: " + order.toString().trim());

        // Both ends at once: a sliding window of the last 3 readings
        Deque<Integer> window = new ArrayDeque<>();
        for (int reading : new int[] {4, 8, 15, 16, 23, 42}) {
            window.addLast(reading);
            if (window.size() > 3) {
                window.removeFirst();
            }
        }
        System.out.println("last 3 readings: " + window);
        System.out.println("peekFirst=" + window.peekFirst() + ", peekLast=" + window.peekLast());

        // poll on empty returns null, remove throws
        Deque<String> empty = new ArrayDeque<>();
        System.out.println("empty.poll(): " + empty.poll());
        try {
            empty.pop();
        } catch (java.util.NoSuchElementException e) {
            System.out.println("empty.pop(): NoSuchElementException");
        }

        // No nulls
        try {
            empty.push(null);
        } catch (NullPointerException e) {
            System.out.println("push(null): NullPointerException");
        }
    }

    private static boolean balanced(String s) {
        Deque<Character> stack = new ArrayDeque<>();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '(', '[', '{' -> stack.push(c);
                case ')', ']', '}' -> {
                    if (stack.isEmpty()) return false;
                    char open = stack.pop();
                    if ("([{".indexOf(open) != ")]}".indexOf(c)) return false;
                }
                default -> { }
            }
        }
        return stack.isEmpty();
    }
}

/* Output:
{[()]} balanced: true
([)] balanced: false
(( balanced: false
BFS order: build test lint report
last 3 readings: [16, 23, 42]
peekFirst=16, peekLast=42
empty.poll(): null
empty.pop(): NoSuchElementException
push(null): NullPointerException
*/
