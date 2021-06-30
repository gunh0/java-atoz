// String.join(delimiter, elements...) glues strings together with a delimiter
// placed only BETWEEN elements, so there is no trailing separator to remove.
// It accepts varargs or any Iterable<? extends CharSequence> (a List, a Set...).
// StringJoiner does the same incrementally and adds an optional prefix and
// suffix; Collectors.joining is the Stream equivalent.
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class JoinExample {
    public static void main(String[] args) {
        // Varargs form
        System.out.println(String.join("-", "2021", "06", "30"));

        // Iterable form
        List<String> languages = List.of("Java", "Kotlin", "Scala");
        System.out.println(String.join(", ", languages));

        // Edge cases: one element -> no delimiter, no elements -> empty string
        System.out.println("[" + String.join(", ", "only") + "]");
        System.out.println("[" + String.join(", ", new ArrayList<String>()) + "]");

        // null elements become the text "null" (but a null delimiter throws)
        System.out.println(String.join("|", "a", null, "c"));

        // The manual loop that join() replaces
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < languages.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(languages.get(i));
        }
        System.out.println(sb);

        // StringJoiner: prefix, suffix, and a value for the empty case
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        joiner.setEmptyValue("EMPTY");
        System.out.println(joiner);
        joiner.add("x=1").add("y=2");
        System.out.println(joiner);

        // Streams: transform, then join
        String csv = languages.stream()
                .map(s -> "\"" + s + "\"")
                .collect(Collectors.joining(",", "[", "]"));
        System.out.println(csv);
    }
}

/* Output:
2021-06-30
Java, Kotlin, Scala
[only]
[]
a|null|c
Java, Kotlin, Scala
EMPTY
{x=1, y=2}
["Java","Kotlin","Scala"]
*/
