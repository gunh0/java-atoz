// Java 12 — Collectors.teeing (API addition, no JEP)
//
// Some results need two aggregations of the same stream: an average needs a
// sum and a count, a range needs a min and a max. Before Java 12 you either
// streamed the data twice (impossible if the source can be consumed only
// once) or wrote a custom collector. Collectors.teeing(c1, c2, merger) sends
// every element to two downstream collectors and combines their results.
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CollectorsTeeing {
    static final class Order {
        private final String customer;
        private final int amount;

        Order(String customer, int amount) {
            this.customer = customer;
            this.amount = amount;
        }

        String customer() {
            return customer;
        }

        int amount() {
            return amount;
        }
    }

    static final class Range {
        final int min, max;

        Range(int min, int max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public String toString() {
            return "Range[min=" + min + ", max=" + max + "]";
        }
    }

    public static void main(String[] args) {
        Supplier<Stream<Order>> orders = () -> Stream.of(
                new Order("kim", 120), new Order("lee", 80),
                new Order("kim", 40), new Order("park", 300), new Order("lee", 60));

        // Before: two passes over the data
        List<Order> all = orders.get().collect(Collectors.toList());
        int total = all.stream().mapToInt(Order::amount).sum();
        long count = all.size();
        System.out.println("two passes: total=" + total + ", count=" + count);

        // After: one pass, two collectors, one merged result
        String summary = orders.get().collect(Collectors.teeing(
                Collectors.summingInt(Order::amount),
                Collectors.counting(),
                (sum, n) -> "total=" + sum + ", count=" + n + ", average=" + sum / n));
        System.out.println("teeing:     " + summary);

        // Min and max at once into a small result type
        Range range = orders.get().map(Order::amount).collect(Collectors.teeing(
                Collectors.minBy(Comparator.<Integer>naturalOrder()),
                Collectors.maxBy(Comparator.<Integer>naturalOrder()),
                (min, max) -> new Range(min.orElseThrow(), max.orElseThrow())));
        System.out.println("range:      " + range);

        // Partition-like split with custom result: big spenders vs. the rest
        var split = orders.get().collect(Collectors.teeing(
                Collectors.filtering(o -> o.amount() >= 100, Collectors.mapping(Order::customer, Collectors.toList())),
                Collectors.filtering(o -> o.amount() < 100, Collectors.counting()),
                (big, smallCount) -> big + " placed big orders; " + smallCount + " small orders"));
        System.out.println(split);

        // Works for an empty stream too: the merger sees empty results
        Optional<Integer> none = Stream.<Integer>empty().collect(Collectors.teeing(
                Collectors.counting(), Collectors.summingInt(i -> i),
                (n, sum) -> n == 0 ? Optional.<Integer>empty() : Optional.of(sum)));
        System.out.println("empty stream: " + none);
    }
}

/* Output:
two passes: total=600, count=5
teeing:     total=600, count=5, average=120
range:      Range[min=40, max=300]
[kim, park] placed big orders; 3 small orders
empty stream: Optional.empty
*/
