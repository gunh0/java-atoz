// java.util.concurrent.Flow defines the Reactive Streams interfaces:
// Publisher, Subscriber, Subscription and Processor. The key idea is
// backpressure: a subscriber says how many items it can take with
// subscription.request(n), and the publisher never sends more than that.
// SubmissionPublisher is the JDK's ready-made Publisher:
//  - submit(item) hands an item to every subscriber, blocking while a
//    subscriber's buffer is full (the producer slows down to the consumer)
//  - offer(item, timeout, unit, onDrop) waits at most that long and then
//    drops the item for that subscriber (the onDrop handler is told)
//  - close() sends onComplete; closeExceptionally(e) sends onError
//  - each subscriber gets its items one at a time, in order, on the
//    publisher's executor
// A Processor is both: it subscribes upstream and publishes downstream,
// which is how transformation stages are built.
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class SubmissionPublisherExample {
    // Requests items in batches and records everything it is told
    static final class BatchSubscriber<T> implements Flow.Subscriber<T> {
        final List<String> events = new ArrayList<>();
        final CountDownLatch subscribed = new CountDownLatch(1);
        final CountDownLatch firstItem = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(1);
        private final int batch;
        private Flow.Subscription subscription;
        private int leftInBatch;

        BatchSubscriber(int batch) {
            this.batch = batch;
        }

        @Override
        public void onSubscribe(Flow.Subscription s) {
            subscription = s;
            subscribed.countDown();
            if (batch > 0) {
                request();
            }
        }

        void request() {
            events.add("request(" + batch + ")");
            leftInBatch = batch;
            subscription.request(batch);
        }

        @Override
        public void onNext(T item) {
            events.add(String.valueOf(item));
            firstItem.countDown();
            if (--leftInBatch == 0) {
                request(); // ask for more only when the batch is processed
            }
        }

        @Override
        public void onError(Throwable t) {
            events.add("onError " + t.getClass().getSimpleName() + ": " + t.getMessage());
            done.countDown();
        }

        @Override
        public void onComplete() {
            events.add("onComplete");
            done.countDown();
        }
    }

    // A transformation stage
    static final class MapProcessor<T, R> extends SubmissionPublisher<R> implements Flow.Processor<T, R> {
        private final Function<T, R> fn;
        private Flow.Subscription upstream;

        MapProcessor(ExecutorService executor, Function<T, R> fn) {
            super(executor, Flow.defaultBufferSize());
            this.fn = fn;
        }

        @Override
        public void onSubscribe(Flow.Subscription s) {
            upstream = s;
            upstream.request(1);
        }

        @Override
        public void onNext(T item) {
            submit(fn.apply(item)); // blocks if OUR subscribers are slow: backpressure travels upstream
            upstream.request(1);
        }

        @Override
        public void onError(Throwable t) {
            closeExceptionally(t);
        }

        @Override
        public void onComplete() {
            close();
        }
    }

    public static void main(String[] args) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            // 1. Demand in batches of 3
            BatchSubscriber<Integer> batches = new BatchSubscriber<>(3);
            try (SubmissionPublisher<Integer> publisher = new SubmissionPublisher<>(executor, 8)) {
                publisher.subscribe(batches);
                for (int i = 1; i <= 7; i++) {
                    publisher.submit(i);
                }
            } // close() -> onComplete after the buffered items
            batches.done.await(5, TimeUnit.SECONDS);
            System.out.println("batches: " + batches.events);

            // 2. publisher -> processor (parse and filter) -> subscriber
            BatchSubscriber<String> sink = new BatchSubscriber<>(Integer.MAX_VALUE);
            MapProcessor<String, String> parse = new MapProcessor<>(executor, line -> line.split(" ")[1]);
            try (SubmissionPublisher<String> lines = new SubmissionPublisher<>(executor, 8)) {
                lines.subscribe(parse);
                parse.subscribe(sink);
                for (String line : List.of("GET /", "POST /login", "GET /home")) {
                    lines.submit(line);
                }
            }
            sink.done.await(5, TimeUnit.SECONDS);
            System.out.println("processor: " + sink.events.subList(1, sink.events.size()));

            // 3. Errors flow downstream as onError
            BatchSubscriber<String> failing = new BatchSubscriber<>(10);
            SubmissionPublisher<String> feed = new SubmissionPublisher<>(executor, 8);
            feed.subscribe(failing);
            feed.submit("tick");
            failing.firstItem.await(5, TimeUnit.SECONDS); // closeExceptionally may discard undelivered items
            feed.closeExceptionally(new IllegalStateException("feed lost"));
            failing.done.await(5, TimeUnit.SECONDS);
            System.out.println("error: " + failing.events);

            // 4. A subscriber that requests nothing: the buffer fills, then offer() drops
            BatchSubscriber<Integer> stalled = new BatchSubscriber<>(0);
            AtomicInteger dropped = new AtomicInteger();
            try (SubmissionPublisher<Integer> ticker = new SubmissionPublisher<>(executor, 4)) {
                ticker.subscribe(stalled);
                stalled.subscribed.await(5, TimeUnit.SECONDS);
                List<Integer> lags = new ArrayList<>();
                for (int i = 1; i <= 6; i++) {
                    int lag = ticker.offer(i, 10, TimeUnit.MILLISECONDS, (sub, item) -> {
                        dropped.incrementAndGet();
                        return false; // do not retry
                    });
                    lags.add(lag);
                }
                System.out.println("offer results (negative = dropped): " + lags + ", onDrop calls: " + dropped.get());
                System.out.println("estimateMaximumLag: " + ticker.estimateMaximumLag());
                stalled.subscription.request(Long.MAX_VALUE); // now take everything that was kept
            }
            stalled.done.await(5, TimeUnit.SECONDS);
            System.out.println("stalled subscriber finally got: " + stalled.events);
        }
    }
}

/* Output:
batches: [request(3), 1, 2, 3, request(3), 4, 5, 6, request(3), 7, onComplete]
processor: [/, /login, /home, onComplete]
error: [request(10), tick, onError IllegalStateException: feed lost]
offer results (negative = dropped): [1, 2, 3, 4, -1, -1], onDrop calls: 2
estimateMaximumLag: 4
stalled subscriber finally got: [1, 2, 3, 4, onComplete]
*/
