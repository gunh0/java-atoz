// indexOf() returns the index of the first occurrence of a char or substring,
// lastIndexOf() the index of the last one; both return -1 when nothing is
// found. The fromIndex overloads start the search at a given position
// (searching forward for indexOf, backward for lastIndexOf), which is the
// usual way to find every occurrence.
public class IndexOfExample {
    public static void main(String[] args) {
        String text = "the cat sat on the mat";

        System.out.println(text.indexOf('a'));          // 5
        System.out.println(text.indexOf("the"));        // 0
        System.out.println(text.lastIndexOf("the"));    // 15
        System.out.println(text.indexOf("dog"));        // -1: not found
        System.out.println(text.indexOf("at", 6));      // 9: search starts at index 6
        System.out.println(text.lastIndexOf("at", 19)); // 9: search goes backward from 19

        // Find all occurrences
        StringBuilder positions = new StringBuilder();
        for (int i = text.indexOf("at"); i >= 0; i = text.indexOf("at", i + 1)) {
            positions.append(i).append(' ');
        }
        System.out.println("\"at\" found at: " + positions.toString().trim());

        // Typical use: split a string at the last separator
        String fileName = "archive.tar.gz";
        int dot = fileName.lastIndexOf('.');
        System.out.println("base: " + fileName.substring(0, dot) + ", extension: " + fileName.substring(dot + 1));

        // Typical use: key=value parsing with the first separator
        String pair = "url=https://example.com/?q=1";
        int eq = pair.indexOf('=');
        System.out.println("key: " + pair.substring(0, eq) + ", value: " + pair.substring(eq + 1));

        // Out-of-range fromIndex values are tolerated, not errors
        System.out.println(text.indexOf('t', 100));     // -1
        System.out.println(text.indexOf('t', -5));      // 0: treated as 0
    }
}

/* Output:
5
0
15
-1
9
9
"at" found at: 5 9 20
base: archive.tar, extension: gz
key: url, value: https://example.com/?q=1
-1
0
*/
