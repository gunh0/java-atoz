// reduce combines all elements into one value by repeatedly applying a
// binary operation. Three forms:
//   reduce(accumulator)                     Optional<T>, empty for no elements
//   reduce(identity, accumulator)           T, identity for no elements
//   reduce(identity, accumulator, combiner) result type U differs from T;
//                                           combiner merges partial results
// Rules: the operation must be associative, and identity must really be
// neutral (op(identity, x) == x), or parallel streams give wrong answers.
// For sums, counts and grouping prefer sum()/count()/collect(): reduce is
// for immutable combining, not for filling a mutable container.
import java.util.List;
import java.util.Optional;
import java.util.function.BinaryOperator;

public class ReduceExample {
    record Response(String path, int status, long bytes) {}

    record Stats(int requests, long bytes) {
        static final Stats EMPTY = new Stats(0, 0);

        Stats add(Response r) {
            return new Stats(requests + 1, bytes + r.bytes());
        }

        Stats merge(Stats other) {
            return new Stats(requests + other.requests, bytes + other.bytes);
        }
    }

    public static void main(String[] args) {
        List<Integer> failedLogins = List.of(3, 0, 7, 1, 4);

        // identity + accumulator
        int total = failedLogins.stream().reduce(0, Integer::sum);
        System.out.println("total failed logins = " + total);

        // accumulator only: Optional, because the stream may be empty
        Optional<Integer> worst = failedLogins.stream().reduce(Integer::max);
        Optional<Integer> none = List.<Integer>of().stream().reduce(Integer::max);
        System.out.println("worst = " + worst + ", on empty = " + none);

        // Any associative operation works: strings, booleans, objects
        String chain = List.of("root", "intermediate", "leaf").stream()
                .reduce((issuer, subject) -> issuer + " -> " + subject).orElse("");
        System.out.println("certificate chain: " + chain);
        boolean allPatched = List.of(true, true, false).stream().reduce(true, Boolean::logicalAnd);
        System.out.println("all hosts patched = " + allPatched);

        // Three-argument form: reduce Responses into a Stats (different type)
        List<Response> responses = List.of(
                new Response("/", 200, 5120), new Response("/login", 401, 230), new Response("/app.js", 200, 20480));
        Stats stats = responses.stream().reduce(Stats.EMPTY, Stats::add, Stats::merge);
        System.out.println(stats);

        // The same thing with map + sum is usually clearer
        long bytes = responses.stream().mapToLong(Response::bytes).sum();
        System.out.println("bytes via mapToLong().sum() = " + bytes);

        // Pitfall 1: a non-neutral identity. Sequentially it is added once...
        System.out.println("reduce(10, sum) sequential = " + failedLogins.stream().reduce(10, Integer::sum));
        // ...but a parallel stream starts EVERY chunk from the identity
        int parallel = failedLogins.parallelStream().reduce(10, Integer::sum);
        System.out.println("reduce(10, sum) parallel > sequential? " + (parallel > 25));

        // Pitfall 2: a non-associative operation (subtraction)
        BinaryOperator<Integer> minus = (a, b) -> a - b;
        System.out.println("(1 - 2) - 3 = " + minus.apply(minus.apply(1, 2), 3)
                + ", 1 - (2 - 3) = " + minus.apply(1, minus.apply(2, 3)));
    }
}

/* Output:
total failed logins = 15
worst = Optional[7], on empty = Optional.empty
certificate chain: root -> intermediate -> leaf
all hosts patched = false
Stats[requests=3, bytes=25830]
bytes via mapToLong().sum() = 25830
reduce(10, sum) sequential = 25
reduce(10, sum) parallel > sequential? true
(1 - 2) - 3 = -4, 1 - (2 - 3) = 2
*/
