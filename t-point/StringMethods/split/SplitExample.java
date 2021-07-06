// split(regex) breaks a string around matches of a REGULAR EXPRESSION and
// returns an array. Points that surprise people:
//  - the argument is a regex, so "." "|" "$" and friends must be escaped
//  - trailing empty strings are removed (unless you pass a negative limit)
//  - a leading empty string is kept when the input starts with a separator
//  - split(regex, limit) with limit > 0 returns at most `limit` parts
import java.util.Arrays;

public class SplitExample {
    public static void main(String[] args) {
        show("a,b,c", "a,b,c".split(","));
        show("words", "split   on  any\twhitespace".split("\\s+"));

        // Regex metacharacters must be escaped
        show("1.2.3 split(\".\")", "1.2.3".split("."));   // every char is a separator
        show("1.2.3 split(\"\\\\.\")", "1.2.3".split("\\."));
        show("a|b split(\"\\\\|\")", "a|b".split("\\|"));

        // Empty strings: trailing ones are dropped, leading ones are kept
        show("a,b,,,", "a,b,,,".split(","));
        show("a,b,,, limit -1", "a,b,,,".split(",", -1));
        show(",a,b", ",a,b".split(","));

        // limit > 0: at most `limit` pieces, the last one keeps the rest
        show("key=value=with=equals limit 2", "key=value=with=equals".split("=", 2));

        // Splitting an empty string yields one empty element, not zero
        System.out.println("\"\".split(\",\").length = " + "".split(",").length);

        // Typical use: parse a CSV line (simple, no quoted fields)
        String line = "Kim, 34 , Seoul";
        String[] fields = line.split("\\s*,\\s*");
        System.out.println("name=" + fields[0] + ", age=" + Integer.parseInt(fields[1].trim()) + ", city=" + fields[2]);
    }

    private static void show(String label, String[] parts) {
        System.out.println(label + " -> " + parts.length + " " + Arrays.toString(parts));
    }
}

/* Output:
a,b,c -> 3 [a, b, c]
words -> 4 [split, on, any, whitespace]
1.2.3 split(".") -> 0 []
1.2.3 split("\\.") -> 3 [1, 2, 3]
a|b split("\\|") -> 2 [a, b]
a,b,,, -> 2 [a, b]
a,b,,, limit -1 -> 5 [a, b, , , ]
,a,b -> 3 [, a, b]
key=value=with=equals limit 2 -> 2 [key, value=with=equals]
"".split(",").length = 1
name=Kim, age=34, city=Seoul
*/
