// JEP 266 — Reactive Streams in java.util.concurrent.Flow (Java 9)
//
// Pushing data from a fast producer to a slow consumer needs back-pressure:
// the consumer must be able to say "send me n more" instead of being
// flooded. Libraries such as RxJava, Reactor and Akka Streams agreed on the
// Reactive Streams interfaces for this, and Java 9 adopted them as
// java.util.concurrent.Flow (Publisher, Subscriber, Subscription,
// Processor) so they can interoperate through the JDK. SubmissionPublisher
// is the ready-made Publisher: submit() hands items to every subscriber
// asynchronously, buffering per subscriber and blocking the producer when
// a subscriber's buffer is full.
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.Function;

public class FlowApi {
    public static void main(String[] args) throws Exception {
        // Before: a plain callback list; no back-pressure, no completion or error signal
        List<String> callbackLog = new ArrayList<>();
        List<java.util.function.Consumer<Integer>> listeners = List.of(n -> callbackLog.add("got " + n));
        for (int i = 1; i <= 3; i++) {
            for (java.util.function.Consumer<Integer> l : listeners) {
                l.accept(i); // the producer decides the pace, the listener just copes
            }
        }
        System.out.println("before: " + callbackLog);

        // After: a subscriber that requests one item at a time
        try (SubmissionPublisher<Integer> publisher = new SubmissionPublisher<>()) {
            OneAtATime<String> subscriber = new OneAtATime<>();
            // A Processor sits in the middle: it subscribes upstream and publishes downstream
            Mapper<Integer, String> squares = new Mapper<>(n -> n + "^2=" + n * n);
            publisher.subscribe(squares);
            squares.subscribe(subscriber);

            for (int i = 1; i <= 5; i++) {
                publisher.submit(i);
            }
            publisher.close(); // signals onComplete after the buffered items
            System.out.println("after: " + subscriber.done.get());
        }

        // Errors travel down the same channel
        try (SubmissionPublisher<Integer> publisher = new SubmissionPublisher<>()) {
            OneAtATime<Integer> subscriber = new OneAtATime<>();
            publisher.subscribe(subscriber);
            publisher.submit(42);
            publisher.closeExceptionally(new IllegalStateException("sensor offline"));
            try {
                subscriber.done.get();
            } catch (java.util.concurrent.ExecutionException e) {
                // closeExceptionally may drop items still buffered, so only the error is certain
                System.out.println("error signal: " + e.getCause().getMessage());
            }
        }

        // Shortcut: consume() subscribes a Consumer and returns a future for completion
        try (SubmissionPublisher<String> publisher = new SubmissionPublisher<>()) {
            List<String> seen = new ArrayList<>();
            CompletableFuture<Void> finished = publisher.consume(seen::add);
            publisher.submit("a");
            publisher.submit("b");
            publisher.close();
            finished.get();
            System.out.println("consume(): " + seen);
        }
    }

    // Requests exactly one item, processes it, then asks for the next one
    static class OneAtATime<T> implements Flow.Subscriber<T> {
        final List<T> received = new ArrayList<>();
        final CompletableFuture<List<T>> done = new CompletableFuture<>();
        private Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(T item) {
            received.add(item);
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            done.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            done.complete(received);
        }
    }

    // Flow.Processor built on SubmissionPublisher: transform and republish
    static class Mapper<T, R> extends SubmissionPublisher<R> implements Flow.Processor<T, R> {
        private final Function<T, R> function;
        private Flow.Subscription subscription;

        Mapper(Function<T, R> function) {
            this.function = function;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(T item) {
            submit(function.apply(item));
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            closeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            close();
        }
    }
}

/* Output:
before: [got 1, got 2, got 3]
after: [1^2=1, 2^2=4, 3^2=9, 4^2=16, 5^2=25]
error signal: sensor offline
consume(): [a, b]
*/
