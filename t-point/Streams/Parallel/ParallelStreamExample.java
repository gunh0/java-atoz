// parallelStream() / .parallel() split the source and run the pipeline on
// the common ForkJoinPool. It is not a free speed-up:
//  - lambdas must not mutate shared state (an ArrayList filled from forEach
//    loses elements or throws); collect into the result instead
//  - forEach runs in any order; forEachOrdered and toList keep encounter order
//  - reduce needs an associative op and a true identity
//  - findAny may return a different element; findFirst costs more in parallel
//  - small inputs, cheap per-element work, blocking I/O and sources that split
//    badly (LinkedList, iterate) usually get slower, not faster
// Measure first; the parallel result must always equal the sequential one.
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ParallelStreamExample {
    public static void main(String[] args) {
        List<Integer> ids = IntStream.rangeClosed(1, 100_000).boxed().toList();

        // Safe: the pipeline produces the result, nothing is shared
        long sumSeq = ids.stream().mapToLong(Integer::longValue).sum();
        long sumPar = ids.parallelStream().mapToLong(Integer::longValue).sum();
        System.out.println("sum sequential == parallel: " + (sumSeq == sumPar) + " (" + sumPar + ")");

        // Order: toList and forEachOrdered keep encounter order even in parallel
        List<Integer> squares = ids.parallelStream().limit(8).map(i -> i * i).toList();
        System.out.println("parallel map + toList keeps order: " + squares);
        StringBuilder ordered = new StringBuilder();
        IntStream.rangeClosed(1, 8).parallel().forEachOrdered(ordered::append);
        System.out.println("forEachOrdered: " + ordered);
        // forEach gives no ordering guarantee, so only print something order-independent
        AtomicInteger seen = new AtomicInteger();
        IntStream.rangeClosed(1, 8).parallel().forEach(i -> seen.incrementAndGet());
        System.out.println("forEach visited " + seen.get() + " elements (in some order)");

        // Unsafe (not run here, its outcome varies from run to run):
        //     List<Integer> unsafe = new ArrayList<>();
        //     ids.parallelStream().forEach(unsafe::add);
        // ArrayList is not thread-safe: elements get lost, or add() throws
        // ArrayIndexOutOfBoundsException. Let the stream build the result instead,
        // or (worse, it serializes the threads) use a synchronized container.
        // Thread-safe alternatives: collect, or a concurrent/synchronized container
        List<Integer> collected = ids.parallelStream().filter(i -> i % 1000 == 0).collect(Collectors.toList());
        List<Integer> synced = Collections.synchronizedList(new ArrayList<>());
        ids.parallelStream().filter(i -> i % 1000 == 0).forEach(synced::add);
        System.out.println("collect size = " + collected.size() + ", in order = " + collected.equals(
                ids.stream().filter(i -> i % 1000 == 0).toList()));
        System.out.println("synchronizedList size = " + synced.size());

        // Concurrent grouping: groupingByConcurrent into a ConcurrentMap
        Map<Integer, Long> byLastDigit = ids.parallelStream()
                .collect(Collectors.groupingByConcurrent(i -> i % 10, ConcurrentHashMap::new, Collectors.counting()));
        System.out.println("count by last digit: " + new TreeMap<>(byLastDigit));

        // A wrong identity is silently multiplied in parallel
        List<Integer> small = ids.subList(0, 1000);
        int seq = small.stream().reduce(1, Integer::sum);
        int par = small.parallelStream().reduce(1, Integer::sum);
        System.out.println("reduce(1, sum) sequential = " + seq + ", parallel adds the 1 per chunk: " + (par > seq));
    }
}

/* Output:
sum sequential == parallel: true (5000050000)
parallel map + toList keeps order: [1, 4, 9, 16, 25, 36, 49, 64]
forEachOrdered: 12345678
forEach visited 8 elements (in some order)
collect size = 100, in order = true
synchronizedList size = 100
count by last digit: {0=10000, 1=10000, 2=10000, 3=10000, 4=10000, 5=10000, 6=10000, 7=10000, 8=10000, 9=10000}
reduce(1, sum) sequential = 500501, parallel adds the 1 per chunk: true
*/
