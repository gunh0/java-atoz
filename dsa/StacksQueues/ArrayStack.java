// A stack is last-in, first-out: push and pop happen at the same end. Backed
// by an array, the "top" is just the index of the next free slot, so push,
// pop and peek are O(1) (push amortized, because the array doubles when full).
// pop() clears the slot it vacates so popped objects can be collected.
// Stacks show up wherever work is nested: call frames, undo history, DFS,
// and matching brackets, which is the self-check here. In Java code, prefer
// ArrayDeque over the legacy synchronized java.util.Stack.
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;

public class ArrayStack {

    static final class Stack<E> {
        private Object[] items = new Object[4];
        private int top; // number of elements == index of the next free slot

        void push(E e) {
            if (top == items.length) {
                items = Arrays.copyOf(items, items.length * 2);
            }
            items[top++] = e;
        }

        @SuppressWarnings("unchecked")
        E pop() {
            if (top == 0) {
                throw new NoSuchElementException("stack underflow");
            }
            E e = (E) items[--top];
            items[top] = null; // avoid loitering references
            return e;
        }

        @SuppressWarnings("unchecked")
        E peek() {
            if (top == 0) {
                throw new NoSuchElementException("empty stack");
            }
            return (E) items[top - 1];
        }

        boolean isEmpty() { return top == 0; }
        int size() { return top; }
    }

    private static final Map<Character, Character> PAIRS = Map.of(')', '(', ']', '[', '}', '{');

    // Every closer must match the most recent unmatched opener
    static boolean balanced(String s) {
        Stack<Character> open = new Stack<>();
        for (char ch : s.toCharArray()) {
            if (ch == '(' || ch == '[' || ch == '{') {
                open.push(ch);
            } else if (PAIRS.containsKey(ch)) {
                if (open.isEmpty() || !open.pop().equals(PAIRS.get(ch))) {
                    return false;
                }
            }
        }
        return open.isEmpty(); // leftovers are unclosed openers
    }

    // Reverse Polish notation: operands are pushed, an operator pops two
    static int evalRpn(String expr) {
        Stack<Integer> st = new Stack<>();
        for (String tok : expr.split(" ")) {
            switch (tok) {
                case "+", "-", "*", "/" -> {
                    int b = st.pop();
                    int a = st.pop();
                    st.push(switch (tok) {
                        case "+" -> a + b;
                        case "-" -> a - b;
                        case "*" -> a * b;
                        default -> a / b;
                    });
                }
                default -> st.push(Integer.parseInt(tok));
            }
        }
        return st.pop();
    }

    public static void main(String[] args) {
        Stack<String> st = new Stack<>();
        for (String s : List.of("first", "second", "third")) {
            st.push(s);
        }
        System.out.println("peek: " + st.peek() + ", size " + st.size());
        StringBuilder order = new StringBuilder();
        while (!st.isEmpty()) {
            order.append(st.pop()).append(' ');
        }
        System.out.println("pop order: " + order.toString().trim());
        try {
            st.pop();
        } catch (NoSuchElementException e) {
            System.out.println("pop on empty -> NoSuchElementException");
        }

        for (String s : List.of("{[()()]}", "([)]", "((", "a(b)c[d]{e}", ")(")) {
            System.out.printf(Locale.ROOT, "%-12s balanced=%b%n", s, balanced(s));
        }
        System.out.println("RPN 3 4 + 2 * 7 - = " + evalRpn("3 4 + 2 * 7 -"));

        // Self-check against ArrayDeque used as a stack
        Random rnd = new Random(5);
        Stack<Integer> mine = new Stack<>();
        Deque<Integer> ref = new ArrayDeque<>();
        boolean same = true;
        for (int op = 0; op < 10_000; op++) {
            if (ref.isEmpty() || rnd.nextInt(3) > 0) {
                int v = rnd.nextInt();
                mine.push(v);
                ref.push(v);
            } else {
                same &= mine.pop().equals(ref.pop());
            }
            same &= mine.size() == ref.size();
        }
        System.out.println("10,000 random push/pop match ArrayDeque: " + same + " (size " + mine.size() + ")");
    }
}

/* Output:
peek: third, size 3
pop order: third second first
pop on empty -> NoSuchElementException
{[()()]}     balanced=true
([)]         balanced=false
((           balanced=false
a(b)c[d]{e}  balanced=true
)(           balanced=false
RPN 3 4 + 2 * 7 - = 7
10,000 random push/pop match ArrayDeque: true (size 3166)
*/
