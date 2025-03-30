// JEP 485 — Stream Gatherers (Java 24)
//
// Streams have a fixed set of intermediate operations (map, filter, flatMap,
// distinct, sorted, limit, ...) and one extensible terminal operation,
// collect(Collector). Anything else that needs state across elements, such
// as fixed-size batches, a sliding window, a running total or "map with at
// most N in flight", had to be faked with a collector that ends the stream,
// an external mutable variable, or a library. Java 24 adds the extensible
// intermediate operation: stream.gather(Gatherer). A gatherer can keep state,
// emit zero or more elements per input, stop early, and emit more at the
// end. java.util.stream.Gatherers ships five: windowFixed, windowSliding,
// fold (a single result emitted at the end, still as a stream element),
// scan (every intermediate result) and mapConcurrent (map on virtual
// threads, at most N at a time, keeping the encounter order).
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Gatherers;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class StreamGatherers {
    public static void main(String[] args) {
        // Before: batching needed an index trick (or a collector plus re-streaming)
        List<Integer> numbers = IntStream.rangeClosed(1, 7).boxed().toList();
        List<List<Integer>> batchesBefore = IntStream.range(0, (numbers.size() + 2) / 3)
                .mapToObj(i -> numbers.subList(i * 3, Math.min(numbers.size(), i * 3 + 3)))
                .toList();
        System.out.println("before, batches of 3:  " + batchesBefore);

        // After: windowFixed works on any stream, even one without random access or an end
        System.out.println("windowFixed(3):        " + numbers.stream().gather(Gatherers.windowFixed(3)).toList());
        System.out.println("windowSliding(3):      " + numbers.stream().gather(Gatherers.windowSliding(3)).toList());

        // Moving average over an endless stream, cut off with limit()
        System.out.println("3-day moving average:  " + Stream.iterate(10.0, x -> x * 1.1)
                .gather(Gatherers.windowSliding(3))
                .map(w -> Math.round(w.stream().mapToDouble(Double::doubleValue).average().orElseThrow() * 10) / 10.0)
                .limit(4).toList());

        // scan: running totals; fold: one result, still flowing on as a stream element
        System.out.println("scan (running sum):    " + numbers.stream().gather(Gatherers.scan(() -> 0, Integer::sum)).toList());
        System.out.println("fold (joined):         " + numbers.stream()
                .gather(Gatherers.fold(() -> "", (acc, n) -> acc.isEmpty() ? "" + n : acc + "-" + n))
                .map(s -> "[" + s + "]").findFirst().orElseThrow());

        // mapConcurrent: blocking calls on virtual threads, at most 3 in flight, order kept
        AtomicInteger inFlight = new AtomicInteger(), maxInFlight = new AtomicInteger();
        List<String> fetched = IntStream.rangeClosed(1, 9).boxed()
                .gather(Gatherers.mapConcurrent(3, id -> {
                    maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                    try {
                        Thread.sleep(Duration.ofMillis(20)); // stands in for an HTTP call
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    inFlight.decrementAndGet();
                    return "user-" + id + (Thread.currentThread().isVirtual() ? "(vt)" : "");
                }))
                .toList();
        System.out.println("mapConcurrent(3):      " + fetched);
        System.out.println("never more than 3 at once: " + (maxInFlight.get() <= 3));

        // Gatherers compose with andThen, and short-circuit like any stream operation
        List<List<Integer>> firstTwoWindows = new ArrayList<>();
        Stream.iterate(1, n -> n + 1)
                .gather(Gatherers.<Integer>windowFixed(2).andThen(Gatherers.windowSliding(2)))
                .limit(2)
                .forEach(w -> firstTwoWindows.add(w.stream().flatMap(List::stream).toList()));
        System.out.println("windowFixed(2) then windowSliding(2), first 2: " + firstTwoWindows);
    }
}

/* Output:
before, batches of 3:  [[1, 2, 3], [4, 5, 6], [7]]
windowFixed(3):        [[1, 2, 3], [4, 5, 6], [7]]
windowSliding(3):      [[1, 2, 3], [2, 3, 4], [3, 4, 5], [4, 5, 6], [5, 6, 7]]
3-day moving average:  [11.0, 12.1, 13.4, 14.7]
scan (running sum):    [1, 3, 6, 10, 15, 21, 28]
fold (joined):         [1-2-3-4-5-6-7]
mapConcurrent(3):      [user-1(vt), user-2(vt), user-3(vt), user-4(vt), user-5(vt), user-6(vt), user-7(vt), user-8(vt), user-9(vt)]
never more than 3 at once: true
windowFixed(2) then windowSliding(2), first 2: [[1, 2, 3, 4], [3, 4, 5, 6]]
*/
