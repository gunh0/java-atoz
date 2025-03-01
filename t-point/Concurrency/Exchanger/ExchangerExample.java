// An Exchanger<V> is a meeting point for exactly two threads: each calls
// exchange(myObject), blocks until the other one arrives, and walks away with
// the other's object. The swap happens atomically, which makes it a natural
// fit for double buffering: a producer fills buffer A while the consumer
// drains buffer B, then they trade buffers and repeat, with no copying and no
// shared queue. exchange(v, timeout, unit) gives up with TimeoutException when
// no partner shows up in time.
// Because the two threads move in lock step, what each side sees is
// deterministic even though they run concurrently.
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ExchangerExample {
    static final int CAPACITY = 4;
    static final List<String> LINES = List.of(
            "GET /", "GET /login", "POST /login", "GET /home", "GET /api/me",
            "POST /api/orders", "GET /api/orders/7", "DELETE /api/orders/7", "GET /logout", "GET /");

    public static void main(String[] args) throws Exception {
        Exchanger<List<String>> exchanger = new Exchanger<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            // Producer: fills its buffer and swaps it for an empty one
            Future<Integer> producer = pool.submit(() -> {
                List<String> buffer = new ArrayList<>(CAPACITY);
                int swaps = 0;
                for (String line : LINES) {
                    buffer.add(line);
                    if (buffer.size() == CAPACITY) {
                        buffer = exchanger.exchange(buffer); // full one out, empty one in
                        swaps++;
                    }
                }
                exchanger.exchange(buffer); // the last, partly filled buffer
                exchanger.exchange(List.of()); // an empty buffer means "done"
                return swaps + 2;
            });

            // Consumer: hands back an emptied buffer each time
            Future<List<String>> consumer = pool.submit(() -> {
                List<String> report = new ArrayList<>();
                List<String> buffer = new ArrayList<>(CAPACITY);
                int batch = 0;
                while (true) {
                    buffer = exchanger.exchange(buffer);
                    if (buffer.isEmpty()) {
                        return report;
                    }
                    batch++;
                    report.add("batch " + batch + " (" + buffer.size() + " lines): " + buffer);
                    buffer.clear(); // the producer no longer holds this one: reuse it
                }
            });

            for (String line : consumer.get()) {
                System.out.println(line);
            }
            System.out.println("producer exchanges: " + producer.get());
        }

        // Nobody on the other side: the timeout variant gives up
        Exchanger<String> lonely = new Exchanger<>();
        try {
            lonely.exchange("hello?", 50, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            System.out.println("no partner within 50 ms: TimeoutException");
        }
    }
}

/* Output:
batch 1 (4 lines): [GET /, GET /login, POST /login, GET /home]
batch 2 (4 lines): [GET /api/me, POST /api/orders, GET /api/orders/7, DELETE /api/orders/7]
batch 3 (2 lines): [GET /logout, GET /]
producer exchanges: 4
no partner within 50 ms: TimeoutException
*/
