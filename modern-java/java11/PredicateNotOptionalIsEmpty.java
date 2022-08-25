// Java 11 — Predicate.not and Optional.isEmpty (API additions, no JEP)
//
// Method references made stream pipelines read well until you needed the
// negation: filter(String::isBlank) is short, but "not blank" forced a
// lambda, filter(s -> !s.isBlank()), or a cast like
// ((Predicate<String>) String::isBlank).negate(). Predicate.not(...) wraps a
// method reference and negates it. In the same spirit Optional gained
// isEmpty(), the readable opposite of isPresent().
import static java.util.function.Predicate.not;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PredicateNotOptionalIsEmpty {
    public static void main(String[] args) {
        List<String> lines = List.of("alpha", "  ", "", "beta", "\t", "gamma");

        // Before: lambda or awkward cast
        List<String> lambda = lines.stream().filter(s -> !s.isBlank()).collect(Collectors.toList());
        List<String> cast = lines.stream()
                .filter(((Predicate<String>) String::isBlank).negate())
                .collect(Collectors.toList());
        // After
        List<String> withNot = lines.stream().filter(not(String::isBlank)).collect(Collectors.toList());
        System.out.println("lambda:  " + lambda);
        System.out.println("negate:  " + cast);
        System.out.println("not():   " + withNot);

        // Works with any method reference, e.g. a set's contains
        List<String> banned = List.of("root", "admin");
        System.out.println("allowed: " + List.of("kim", "root", "lee", "admin").stream()
                .filter(not(banned::contains)).collect(Collectors.toList()));

        // Optional.isEmpty instead of !isPresent()
        Map<String, String> emails = Map.of("kim", "kim@example.com");
        for (String user : List.of("kim", "lee")) {
            Optional<String> email = Optional.ofNullable(emails.get(user));
            if (email.isEmpty()) {
                System.out.println(user + ": no email on file");
            } else {
                System.out.println(user + ": " + email.get());
            }
        }

        // And in pipelines: Optional::isEmpty as a predicate
        long missing = List.of("kim", "lee", "park").stream()
                .map(u -> Optional.ofNullable(emails.get(u)))
                .filter(Optional::isEmpty)
                .count();
        System.out.println("users without email: " + missing);
    }
}

/* Output:
lambda:  [alpha, beta, gamma]
negate:  [alpha, beta, gamma]
not():   [alpha, beta, gamma]
allowed: [kim, lee]
kim: kim@example.com
lee: no email on file
users without email: 2
*/
