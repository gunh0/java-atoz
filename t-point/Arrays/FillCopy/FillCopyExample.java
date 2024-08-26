// Creating, filling and copying arrays with java.util.Arrays and System:
//  - fill(a, v) / fill(a, from, to, v) writes one value; setAll(a, i -> ...)
//    computes each element from its index
//  - copyOf(a, n) truncates or pads with the default value (0, null, false);
//    copyOfRange(a, from, to) slices; both return a new array
//  - System.arraycopy copies between existing arrays and handles overlap
//  - fill with an object fills every slot with the SAME reference
//  - Arrays.asList is a fixed-size view backed by the array: set() writes
//    through, add()/remove() throw UnsupportedOperationException
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FillCopyExample {
    public static void main(String[] args) {
        int[] scores = new int[6];
        Arrays.fill(scores, -1);
        Arrays.fill(scores, 2, 4, 0);
        System.out.println("fill:        " + Arrays.toString(scores));

        long[] powers = new long[8];
        Arrays.setAll(powers, i -> 1L << i);
        System.out.println("setAll:      " + Arrays.toString(powers));

        // copyOf: grow (padding) and shrink (truncation)
        int[] base = {1, 2, 3};
        System.out.println("copyOf 5:    " + Arrays.toString(Arrays.copyOf(base, 5)));
        System.out.println("copyOf 2:    " + Arrays.toString(Arrays.copyOf(base, 2)));
        String[] padded = Arrays.copyOf(new String[] {"a", "b"}, 4);
        System.out.println("copyOf str:  " + Arrays.toString(padded));
        System.out.println("copyOfRange: " + Arrays.toString(Arrays.copyOfRange(powers, 2, 5)));

        // clone() and copyOf are shallow: the inner arrays are shared
        int[][] grid = {{1, 2}, {3, 4}};
        int[][] shallow = grid.clone();
        shallow[0][0] = 99;
        System.out.println("shallow copy changed original: " + Arrays.deepToString(grid));

        // System.arraycopy with overlapping ranges (shift right by one)
        char[] text = "abcdef".toCharArray();
        System.arraycopy(text, 0, text, 1, text.length - 1);
        text[0] = '_';
        System.out.println("shift right: " + new String(text));

        // fill with an object: one shared instance
        StringBuilder[] buffers = new StringBuilder[3];
        Arrays.fill(buffers, new StringBuilder());
        buffers[0].append("x");
        System.out.println("shared fill: " + Arrays.toString(buffers) + " same=" + (buffers[0] == buffers[2]));
        Arrays.setAll(buffers, i -> new StringBuilder());
        buffers[0].append("x");
        System.out.println("setAll new:  " + Arrays.toString(buffers));

        // Arrays.asList: fixed-size, write-through view
        String[] hosts = {"db", "cache", "web"};
        List<String> view = Arrays.asList(hosts);
        view.set(0, "db-primary");
        System.out.println("array after view.set: " + Arrays.toString(hosts));
        hosts[2] = "web-1";
        System.out.println("view after array write: " + view);
        try {
            view.add("queue");
        } catch (UnsupportedOperationException e) {
            System.out.println("view.add -> " + e.getClass().getSimpleName());
        }
        List<String> growable = new ArrayList<>(view);
        growable.add("queue");
        System.out.println("copied into ArrayList: " + growable);

        // asList on a primitive array gives a List<int[]> of one element
        int[] numbers = {3, 1, 2};
        System.out.println("asList(int[]).size() = " + Arrays.asList(numbers).size());
        System.out.println("boxed instead: " + Arrays.stream(numbers).boxed().toList());
    }
}

/* Output:
fill:        [-1, -1, 0, 0, -1, -1]
setAll:      [1, 2, 4, 8, 16, 32, 64, 128]
copyOf 5:    [1, 2, 3, 0, 0]
copyOf 2:    [1, 2]
copyOf str:  [a, b, null, null]
copyOfRange: [4, 8, 16]
shallow copy changed original: [[99, 2], [3, 4]]
shift right: _abcde
shared fill: [x, x, x] same=true
setAll new:  [x, , ]
array after view.set: [db-primary, cache, web]
view after array write: [db-primary, cache, web-1]
view.add -> UnsupportedOperationException
copied into ArrayList: [db-primary, cache, web-1, queue]
asList(int[]).size() = 1
boxed instead: [3, 1, 2]
*/
