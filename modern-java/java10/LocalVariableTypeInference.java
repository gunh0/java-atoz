// JEP 286 — Local-Variable Type Inference (Java 10)
//
// `var` lets the compiler infer the static type of a local variable from its
// initializer, removing redundant noise such as
//   Map<String, List<Integer>> m = new HashMap<String, List<Integer>>();
// Java stays statically typed: the inferred type is fixed at compile time.
// `var` is a reserved type name (not a keyword), it works only for locals with
// an initializer, for-loop variables and try-with-resources, never for fields,
// parameters or return types. Use it when the right-hand side already makes
// the type obvious.
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocalVariableTypeInference {
    public static void main(String[] args) throws Exception {
        // Before
        Map<String, List<Integer>> scoresBefore = new HashMap<String, List<Integer>>();
        // After: the type is spelled once
        var scores = new HashMap<String, List<Integer>>();
        scores.put("alice", List.of(90, 85));
        scores.put("bob", List.of(70));
        scoresBefore.putAll(scores);

        // Loops
        for (var entry : new java.util.TreeMap<>(scores).entrySet()) {
            var total = 0; // int
            for (var s : entry.getValue()) {
                total += s;
            }
            System.out.println(entry.getKey() + " -> " + total);
        }

        // try-with-resources
        try (var reader = new BufferedReader(new StringReader("first line\nsecond line"))) {
            System.out.println("read: " + reader.readLine());
        }

        // Still statically typed: the inferred type is fixed
        var count = 10;           // int
        // count = "ten";         // does not compile: incompatible types
        count += 5;
        System.out.println("count = " + count);

        // Pitfall: var + diamond infers ArrayList<Object>
        var things = new ArrayList<>();
        things.add("text");
        things.add(42);           // compiles: element type is Object
        System.out.println("things = " + things);

        // Pitfall: literals infer their own type
        var big = 3_000_000_000L; // long, because of the L suffix
        var ratio = 1 / 2;        // int division -> 0
        System.out.println("big + 1 = " + (big + 1) + ", ratio = " + ratio);

        // Bonus: var can name a non-denotable type (an anonymous class),
        // so its extra members stay accessible
        var point = new Object() {
            int x = 3;
            int y = 4;
        };
        System.out.println("anonymous point: " + point.x + ", " + point.y);

        // Not allowed (compile errors):
        // var nothing;            // no initializer
        // var nil = null;         // type cannot be inferred
        // var lambda = () -> 1;   // lambda needs an explicit target type
        // var array = {1, 2, 3};  // array initializer needs an explicit type
    }
}

/* Output:
alice -> 175
bob -> 70
read: first line
count = 15
things = [text, 42]
big + 1 = 3000000001, ratio = 0
anonymous point: 3, 4
*/
