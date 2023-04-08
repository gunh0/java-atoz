// JEP 323 — Local-Variable Syntax for Lambda Parameters (Java 11)
//
// Implicitly typed lambda parameters, (a, b) -> ..., cannot carry
// modifiers or annotations: there is nowhere to put them. To mark a
// parameter final or @Nonnull you had to spell out the full type, which
// can be long (Map.Entry<String, List<Integer>>) or impossible to write
// (an anonymous or intersection type). Java 11 allows `var` for lambda
// parameters: the types are still inferred, but now there is a slot for
// modifiers and annotations, used by static analysers and code checkers.
// Rules: all parameters use var or none do, and var cannot be mixed with
// explicit types.
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

public class VarLambdaParameters {
    // A marker for tools (think of a checker that tracks tainted input)
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
    @interface Sanitized {
    }

    public static void main(String[] args) {
        // Before: to annotate, every parameter type had to be written out
        BiFunction<Map.Entry<String, List<Integer>>, Integer, String> verbose =
                (@Sanitized Map.Entry<String, List<Integer>> entry, final Integer limit) ->
                        entry.getKey() + ":" + entry.getValue().stream().limit(limit).collect(Collectors.toList());

        // Implicit types: short, but no place for @Sanitized or final
        BiFunction<Map.Entry<String, List<Integer>>, Integer, String> implicit =
                (entry, limit) ->
                        entry.getKey() + ":" + entry.getValue().stream().limit(limit).collect(Collectors.toList());

        // After: var keeps inference AND accepts modifiers and annotations
        BiFunction<Map.Entry<String, List<Integer>>, Integer, String> withVar =
                (@Sanitized var entry, final var limit) ->
                        entry.getKey() + ":" + entry.getValue().stream().limit(limit).collect(Collectors.toList());

        Map<String, List<Integer>> scores = new TreeMap<>(Map.of("alice", List.of(90, 75, 88), "bob", List.of(60)));
        for (Map.Entry<String, List<Integer>> e : scores.entrySet()) {
            System.out.println(verbose.apply(e, 2) + " | " + implicit.apply(e, 2) + " | " + withVar.apply(e, 2));
        }

        // var parameters are inferred exactly like implicit ones
        BinaryOperator<String> join = (final var left, final var right) -> left + "+" + right;
        System.out.println(join.apply("var", "lambda"));

        // Not allowed (compile errors):
        //   (var a, b) -> ...          mixing var and implicit
        //   (var a, String b) -> ...   mixing var and explicit
        //   var a -> ...               var needs parentheses
    }
}

/* Output:
alice:[90, 75] | alice:[90, 75] | alice:[90, 75]
bob:[60] | bob:[60] | bob:[60]
var+lambda
*/
