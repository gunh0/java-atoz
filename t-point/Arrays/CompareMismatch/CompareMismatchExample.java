// Arrays do not override equals/hashCode/toString, so == and a.equals(b)
// compare references and println shows something like "[I@1b6d3586".
// Use the static helpers in java.util.Arrays instead:
//  - equals / hashCode / toString look at the elements, one level deep
//  - deepEquals / deepHashCode / deepToString recurse into nested arrays
//  - compare(a, b) orders arrays lexicographically (a shorter prefix
//    comes first); mismatch(a, b) returns the first differing index, or -1
// Ranged overloads (Java 9) compare slices without copying them.
import java.util.Arrays;

public class CompareMismatchExample {
    public static void main(String[] args) {
        int[] a = {1, 2, 3};
        int[] b = {1, 2, 3};
        System.out.println("a == b:             " + (a == b));
        System.out.println("a.equals(b):        " + a.equals(b));
        System.out.println("Arrays.equals(a,b): " + Arrays.equals(a, b));
        System.out.println("same hashCode:      " + (Arrays.hashCode(a) == Arrays.hashCode(b)));
        System.out.println("toString:           " + Arrays.toString(a));

        // Nested arrays need the deep variants
        int[][] m1 = {{1, 2}, {3, 4}};
        int[][] m2 = {{1, 2}, {3, 4}};
        System.out.println("equals(nested):     " + Arrays.equals(m1, m2));
        System.out.println("deepEquals(nested): " + Arrays.deepEquals(m1, m2));
        System.out.println("deepToString:       " + Arrays.deepToString(m1));
        System.out.println("deepHashCode same:  " + (Arrays.deepHashCode(m1) == Arrays.deepHashCode(m2)));

        // compare: lexicographic, like String.compareTo
        int[][] versions = {{1, 10, 0}, {1, 9, 3}, {1, 10}, {2}};
        for (int[] v : versions) {
            System.out.printf("compare(%s, [1, 10]) = %d%n",
                    Arrays.toString(v), Integer.signum(Arrays.compare(v, new int[] {1, 10})));
        }
        int[][] sorted = versions.clone();
        Arrays.sort(sorted, Arrays::compare);
        System.out.println("versions sorted: " + Arrays.deepToString(sorted));

        // compareUnsigned treats bytes as 0..255
        byte[] x = {(byte) 0x7F};
        byte[] y = {(byte) 0x80};
        System.out.println("compare signed:   " + Integer.signum(Arrays.compare(x, y)));
        System.out.println("compare unsigned: " + Integer.signum(Arrays.compareUnsigned(x, y)));

        // mismatch: where do two arrays start to differ?
        char[] expected = "GET /index.html HTTP/1.1".toCharArray();
        char[] actual = "GET /index.htm HTTP/1.1".toCharArray();
        int i = Arrays.mismatch(expected, actual);
        System.out.println("mismatch at " + i + ": '" + expected[i] + "' vs '" + actual[i] + "'");
        System.out.println("mismatch(equal) = " + Arrays.mismatch(a, b));
        System.out.println("mismatch(prefix) = " + Arrays.mismatch(new int[] {1, 2}, new int[] {1, 2, 3}));

        // Ranged equals: compare a slice without copying
        byte[] packet = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00};
        byte[] zipMagic = {0x50, 0x4B, 0x03, 0x04};
        System.out.println("starts with zip magic: " + Arrays.equals(packet, 0, 4, zipMagic, 0, 4));
    }
}

/* Output:
a == b:             false
a.equals(b):        false
Arrays.equals(a,b): true
same hashCode:      true
toString:           [1, 2, 3]
equals(nested):     false
deepEquals(nested): true
deepToString:       [[1, 2], [3, 4]]
deepHashCode same:  true
compare([1, 10, 0], [1, 10]) = 1
compare([1, 9, 3], [1, 10]) = -1
compare([1, 10], [1, 10]) = 0
compare([2], [1, 10]) = 1
versions sorted: [[1, 9, 3], [1, 10], [1, 10, 0], [2]]
compare signed:   1
compare unsigned: -1
mismatch at 14: 'l' vs ' '
mismatch(equal) = -1
mismatch(prefix) = 2
starts with zip magic: true
*/
