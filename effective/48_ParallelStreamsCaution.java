// Effective Java, Item 48: Use caution when making streams parallel.
//
// Adding .parallel() is easy; getting a correct and faster result is not.
// Parallelism rarely helps when the source is Stream.iterate or the pipeline
// uses limit, because neither can be split well. The best sources are
// ArrayList, HashMap, arrays and int/long ranges: they split cheaply and
// have good locality. Correctness needs the same care: reduction functions
// must be associative, non-interfering and stateless, and the identity must
// really be an identity, or the result depends on how the work was split.
// Do not parallelise without a reason and a measurement; when it fits (a
// CPU-bound reduction over a splittable source) the change can be one word.
// This demo prints only results, not timings.
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class ParallelStreamsCaution {
    public static void main(String[] args) {
        // A good fit: counting primes over a LongStream range (splits evenly)
        long n = 100_000;
        long sequential = LongStream.rangeClosed(2, n).filter(ParallelStreamsCaution::isPrime).count();
        long parallel = LongStream.rangeClosed(2, n).parallel().filter(ParallelStreamsCaution::isPrime).count();
        System.out.println("pi(" + n + ") sequential = " + sequential + ", parallel = " + parallel);

        // A broken "identity": 5 is not the identity of addition. Sequentially it is
        // added once; in parallel it is added once per split chunk.
        int seqBad = IntStream.rangeClosed(1, 1000).boxed().reduce(5, Integer::sum);
        int parBad = IntStream.rangeClosed(1, 1000).boxed().parallel().reduce(5, Integer::sum);
        System.out.println("reduce(5, sum) sequential = " + seqBad);
        System.out.println("reduce(5, sum) parallel equals sequential? " + (parBad == seqBad));

        // Correct: a true identity (0) and an associative operation
        int seqOk = IntStream.rangeClosed(1, 1000).reduce(0, Integer::sum);
        int parOk = IntStream.rangeClosed(1, 1000).parallel().reduce(0, Integer::sum);
        System.out.println("reduce(0, sum) sequential = " + seqOk + ", parallel = " + parOk);

        // Collect, do not add to a shared ArrayList from forEach (that races)
        List<Integer> squares = IntStream.range(0, 10_000).parallel()
                .map(i -> i * i).boxed().collect(Collectors.toList());
        System.out.println("parallel collect keeps order and size: " + squares.size()
                + " elements, last = " + squares.get(squares.size() - 1));

        // forEach on a parallel stream has no order; forEachOrdered restores it
        StringBuilder ordered = new StringBuilder();
        IntStream.range(0, 10).parallel().forEachOrdered(ordered::append);
        System.out.println("forEachOrdered: " + ordered);
    }

    static boolean isPrime(long x) {
        if (x < 2) return false;
        for (long d = 2; d * d <= x; d++) {
            if (x % d == 0) return false;
        }
        return true;
    }
}

/* Output:
pi(100000) sequential = 9592, parallel = 9592
reduce(5, sum) sequential = 500505
reduce(5, sum) parallel equals sequential? false
reduce(0, sum) sequential = 500500, parallel = 500500
parallel collect keeps order and size: 10000 elements, last = 99980001
forEachOrdered: 0123456789
*/
