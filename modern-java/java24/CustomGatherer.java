// JEP 485 — Stream Gatherers: writing your own Gatherer (Java 24)
//
// The built-in gatherers (StreamGatherers.java) cover windows, folds, scans
// and concurrent mapping. For anything else, implement Gatherer<T, A, R>,
// which mirrors Collector with four functions:
//   initializer  creates the private state A (e.g. a HashSet, a counter);
//   integrator   receives each element plus the state and a Downstream, may
//                push zero or more R values, and returns false to stop the
//                stream early (that is how a gatherer short-circuits);
//   combiner     merges two states, which makes the gatherer usable in
//                parallel streams (without one it runs sequentially);
//   finisher     runs at the end and may push what is still buffered.
// Factory methods build them from lambdas: Gatherer.ofSequential(...) and
// Gatherer.of(...); Integrator.ofGreedy marks an integrator that never stops
// early, which lets the stream skip some bookkeeping. Before Java 24 the same
// logic needed a mutable variable captured by a lambda (not thread-safe, not
// reusable) or a terminal collector followed by re-streaming.
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Gatherer;
import java.util.stream.Gatherers;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class CustomGatherer {
    // distinctBy: keep the first element for each key. Sequential, greedy.
    static <T, K> Gatherer<T, ?, T> distinctBy(Function<? super T, ? extends K> key) {
        return Gatherer.ofSequential(
                () -> new HashSet<K>(),
                Gatherer.Integrator.ofGreedy((seen, element, downstream) -> {
                    if (seen.add(key.apply(element))) return downstream.push(element);
                    return true;
                }));
    }

    // takeUntilBudget: pass elements while their running cost fits the budget, then stop the stream
    static Gatherer<Integer, ?, Integer> takeUntilBudget(int budget) {
        class Spent { int total; }
        return Gatherer.ofSequential(Spent::new, (spent, cost, downstream) -> {
            if (spent.total + cost > budget) return false; // short-circuit: upstream stops producing
            spent.total += cost;
            return downstream.push(cost);
        });
    }

    // runLengths, with a finisher: buffers the current run, emits it on change and at the end
    static <T> Gatherer<T, ?, String> runLengths() {
        class Run { T value; int count; }
        return Gatherer.ofSequential(
                Run::new,
                Gatherer.Integrator.<Run, T, String>ofGreedy((run, element, downstream) -> {
                    if (run.count > 0 && Objects.equals(run.value, element)) { run.count++; return true; }
                    boolean more = run.count == 0 || downstream.push(run.value + "x" + run.count);
                    run.value = element;
                    run.count = 1;
                    return more;
                }),
                (run, downstream) -> { if (run.count > 0) downstream.push(run.value + "x" + run.count); });
    }

    // A parallel-capable gatherer: per-thread state, combined, emitted once by the finisher
    static Gatherer<Integer, ?, String> minMax() {
        class MinMax { int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE; }
        return Gatherer.of(
                MinMax::new,
                Gatherer.Integrator.ofGreedy((mm, n, downstream) -> {
                    mm.min = Math.min(mm.min, n);
                    mm.max = Math.max(mm.max, n);
                    return true;
                }),
                (a, b) -> { a.min = Math.min(a.min, b.min); a.max = Math.max(a.max, b.max); return a; },
                (mm, downstream) -> downstream.push("min=" + mm.min + " max=" + mm.max));
    }

    record User(String email, String name) {}

    public static void main(String[] args) {
        List<User> users = List.of(new User("a@x.io", "Ann"), new User("b@x.io", "Bob"),
                new User("A@X.IO", "Ann (duplicate)"), new User("c@x.io", "Cy"));

        // Before: a set captured by the lambda; works only sequentially and is easy to share by accident
        Set<String> seen = new HashSet<>();
        List<String> before = users.stream().filter(u -> seen.add(u.email().toLowerCase())).map(User::name).toList();
        System.out.println("before, distinct by email: " + before);
        System.out.println("distinctBy(email):         " + users.stream()
                .gather(distinctBy(u -> u.email().toLowerCase())).map(User::name).toList());

        // Short-circuiting works on an infinite stream
        List<Integer> generated = new ArrayList<>();
        List<Integer> taken = Stream.iterate(1, n -> n + 1).peek(generated::add).gather(takeUntilBudget(10)).toList();
        System.out.println("takeUntilBudget(10):       " + taken + ", elements generated: " + generated.size());

        System.out.println("runLengths:                " + Stream.of("a", "a", "b", "c", "c", "c", "a").gather(runLengths()).toList());

        System.out.println("minMax, sequential:        " + IntStream.range(-50, 1000).boxed().gather(minMax()).toList());
        System.out.println("minMax, parallel:          " + IntStream.range(-50, 1000).boxed().parallel().gather(minMax()).toList());

        // Gatherers are values: reuse them and chain with andThen
        var pipeline = CustomGatherer.<String, String>distinctBy(String::toLowerCase).andThen(Gatherers.windowFixed(2));
        System.out.println("distinctBy, then windowFixed(2): "
                + Stream.of("Go", "go", "Java", "JAVA", "Rust", "java").gather(pipeline).toList());
    }
}

/* Output:
before, distinct by email: [Ann, Bob, Cy]
distinctBy(email):         [Ann, Bob, Cy]
takeUntilBudget(10):       [1, 2, 3, 4], elements generated: 5
runLengths:                [ax2, bx1, cx3, ax1]
minMax, sequential:        [min=-50 max=999]
minMax, parallel:          [min=-50 max=999]
distinctBy, then windowFixed(2): [[Go, Java], [Rust]]
*/
