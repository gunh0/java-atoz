// A Spliterator is the source behind every Stream: it hands out elements
// (tryAdvance / forEachRemaining) and can split itself in two (trySplit) so a
// parallel stream can work on both halves at once.
//  - tryAdvance(action): give the next element to action, false at the end
//  - trySplit(): return a new Spliterator for a PREFIX of the remaining
//    elements and keep the rest, or null if too small to split
//  - estimateSize(): remaining elements (exact when SIZED)
//  - characteristics(): ORDERED, SIZED, SUBSIZED, SORTED, DISTINCT,
//    NONNULL, IMMUTABLE, CONCURRENT; the stream uses them to optimize, e.g. a
//    SIZED source lets count() skip the traversal entirely
// StreamSupport.stream(spliterator, parallel) turns it into a Stream.
// Writing one pays off for sources that are not collections: a ring buffer, a
// file of fixed-size records, a paged API, or a "chunk by N" adapter.
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class SpliteratorExample {
    // 1. Fixed-size records packed in one long[] (e.g. read from a binary file)
    record Reading(int sensor, long value) {}

    static final class ReadingSpliterator implements Spliterator<Reading> {
        private final long[] data; // pairs: sensor, value, sensor, value, ...
        private int index;         // next record
        private final int end;     // exclusive, in records

        ReadingSpliterator(long[] data, int start, int end) {
            this.data = data;
            this.index = start;
            this.end = end;
        }

        @Override
        public boolean tryAdvance(Consumer<? super Reading> action) {
            if (index >= end) {
                return false;
            }
            action.accept(new Reading((int) data[2 * index], data[2 * index + 1]));
            index++;
            return true;
        }

        @Override
        public Spliterator<Reading> trySplit() {
            int remaining = end - index;
            if (remaining < 4) {
                return null; // not worth splitting
            }
            int mid = index + remaining / 2;
            Spliterator<Reading> prefix = new ReadingSpliterator(data, index, mid);
            index = mid;
            return prefix;
        }

        @Override
        public long estimateSize() {
            return end - index;
        }

        @Override
        public int characteristics() {
            return ORDERED | SIZED | SUBSIZED | NONNULL | IMMUTABLE;
        }
    }

    // 2. An adapter that groups any source into lists of n (the last may be shorter)
    static <T> Stream<List<T>> chunked(Stream<T> source, int n) {
        Spliterator<T> in = source.spliterator();
        Spliterator<List<T>> out = new Spliterators.AbstractSpliterator<>(
                (in.estimateSize() + n - 1) / n, Spliterator.ORDERED | Spliterator.NONNULL) {
            @Override
            public boolean tryAdvance(Consumer<? super List<T>> action) {
                List<T> chunk = new ArrayList<>(n);
                while (chunk.size() < n && in.tryAdvance(chunk::add)) {
                    // keep pulling
                }
                if (chunk.isEmpty()) {
                    return false;
                }
                action.accept(chunk);
                return true;
            }
        }; // AbstractSpliterator implements trySplit by buffering batches
        return StreamSupport.stream(out, false);
    }

    public static void main(String[] args) {
        int records = 1000;
        long[] data = new long[2 * records];
        for (int i = 0; i < records; i++) {
            data[2 * i] = i % 4;    // sensor id
            data[2 * i + 1] = i * 3; // value
        }

        // Splitting by hand shows what a parallel stream does internally
        Spliterator<Reading> whole = new ReadingSpliterator(data, 0, records);
        Spliterator<Reading> firstHalf = whole.trySplit();
        Spliterator<Reading> firstQuarter = firstHalf.trySplit();
        System.out.println("after two splits: " + firstQuarter.estimateSize() + " + "
                + firstHalf.estimateSize() + " + " + whole.estimateSize());
        System.out.println("tiny spliterator splits? " + (new ReadingSpliterator(data, 0, 3).trySplit() != null));

        // Same answer sequentially and in parallel
        long sequential = StreamSupport.stream(new ReadingSpliterator(data, 0, records), false)
                .filter(r -> r.sensor() == 2).mapToLong(Reading::value).sum();
        long parallel = StreamSupport.stream(new ReadingSpliterator(data, 0, records), true)
                .filter(r -> r.sensor() == 2).mapToLong(Reading::value).sum();
        System.out.println("sensor 2 total: sequential " + sequential + ", parallel " + parallel);
        List<Integer> firstSensors = StreamSupport.stream(new ReadingSpliterator(data, 0, records), true)
                .map(Reading::sensor).limit(6).toList();
        System.out.println("ORDERED keeps encounter order in parallel: " + firstSensors);

        // SIZED: count() can answer from estimateSize() without visiting elements
        AtomicInteger visited = new AtomicInteger();
        long count = StreamSupport.stream(new ReadingSpliterator(data, 0, records), false)
                .peek(r -> visited.incrementAndGet()).count();
        System.out.println("count() = " + count + ", elements visited: " + visited.get());

        // An Iterator of unknown size can be wrapped too, at the cost of poor splitting
        Iterator<String> pages = List.of("page1", "page2", "page3").iterator();
        Spliterator<String> unknown = Spliterators.spliteratorUnknownSize(pages, Spliterator.ORDERED);
        System.out.println("unknown size estimate is Long.MAX_VALUE: " + (unknown.estimateSize() == Long.MAX_VALUE)
                + ", SIZED: " + unknown.hasCharacteristics(Spliterator.SIZED));
        System.out.println("pages: " + StreamSupport.stream(unknown, false).toList());

        // The chunking adapter
        System.out.println("chunks of 4: " + chunked(IntStream.rangeClosed(1, 10).boxed(), 4).toList());
        System.out.println("chunks of 3: " + chunked(Stream.of("a", "b", "c", "d", "e", "f"), 3).toList());
    }
}

/* Output:
after two splits: 250 + 250 + 500
tiny spliterator splits? false
sensor 2 total: sequential 375000, parallel 375000
ORDERED keeps encounter order in parallel: [0, 1, 2, 3, 0, 1]
count() = 1000, elements visited: 0
unknown size estimate is Long.MAX_VALUE: true, SIZED: false
pages: [page1, page2, page3]
chunks of 4: [[1, 2, 3, 4], [5, 6, 7, 8], [9, 10]]
chunks of 3: [[a, b, c], [d, e, f]]
*/
