// ArrayList is a resizable array: the default List implementation.
//  - get(i) / set(i, x) are O(1) because elements sit in one backing array
//  - add(x) at the end is amortized O(1); when the array is full it grows by ~50%
//  - add(i, x) / remove(i) in the middle shift every later element: O(n)
//  - remove(int) removes by INDEX, remove(Object) removes by VALUE; with a
//    List<Integer> this overload is a classic trap
//  - Arrays.asList() and List.of() are not ArrayLists: copy them into
//    new ArrayList<>(...) when you need a list you can grow
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArrayListExample {
    public static void main(String[] args) {
        List<String> tasks = new ArrayList<>();
        tasks.add("write tests");
        tasks.add("fix bug #42");
        tasks.add("review PR");
        tasks.add(1, "reproduce bug #42"); // inserts and shifts the rest right
        System.out.println("tasks: " + tasks + ", size=" + tasks.size());

        System.out.println("get(2): " + tasks.get(2));
        String old = tasks.set(0, "write more tests"); // set returns the old value
        System.out.println("set(0) replaced: " + old);
        System.out.println("indexOf(\"review PR\"): " + tasks.indexOf("review PR"));
        System.out.println("contains(\"deploy\"): " + tasks.contains("deploy"));

        // remove(int) vs remove(Object) on a List<Integer>
        List<Integer> ports = new ArrayList<>(List.of(22, 80, 443, 1));
        ports.remove(1);                   // index 1 -> removes 80
        System.out.println("after remove(1): " + ports);
        ports.remove(Integer.valueOf(1));  // the value 1
        System.out.println("after remove(Integer.valueOf(1)): " + ports);

        // Out-of-range indexes fail fast
        try {
            tasks.get(10);
        } catch (IndexOutOfBoundsException e) {
            System.out.println("get(10): " + e.getMessage());
        }

        // Arrays.asList is a fixed-size view of the array
        List<String> fixed = Arrays.asList("a", "b");
        try {
            fixed.add("c");
        } catch (UnsupportedOperationException e) {
            System.out.println("Arrays.asList add: UnsupportedOperationException");
        }
        List<String> growable = new ArrayList<>(fixed);
        growable.add("c");
        System.out.println("copied into ArrayList: " + growable);

        // Bulk operations and sublist views
        List<String> done = List.of("fix bug #42", "review PR");
        tasks.removeAll(done);
        System.out.println("after removeAll(done): " + tasks);
        List<Integer> numbers = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));
        numbers.subList(1, 4).clear(); // a view: clearing it removes from the list
        System.out.println("after subList(1, 4).clear(): " + numbers);

        // Index loop vs for-each
        for (int i = 0; i < tasks.size(); i++) {
            System.out.println(i + ": " + tasks.get(i));
        }
    }
}

/* Output:
tasks: [write tests, reproduce bug #42, fix bug #42, review PR], size=4
get(2): fix bug #42
set(0) replaced: write tests
indexOf("review PR"): 3
contains("deploy"): false
after remove(1): [22, 443, 1]
after remove(Integer.valueOf(1)): [22, 443]
get(10): Index 10 out of bounds for length 4
Arrays.asList add: UnsupportedOperationException
copied into ArrayList: [a, b, c]
after removeAll(done): [write more tests, reproduce bug #42]
after subList(1, 4).clear(): [1, 5, 6]
0: write more tests
1: reproduce bug #42
*/
