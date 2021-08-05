// A synchronized block locks only the statements that need it:
//     synchronized (lockObject) { ...critical section... }
// Compared with a synchronized method it lets you
//  - keep slow work that touches no shared state OUTSIDE the lock, and
//  - choose the lock: a private final lock object cannot be grabbed by
//    outside code (anyone can synchronize on your `this`), and independent
//    pieces of state can use independent locks so they do not block each other.
import java.util.ArrayList;
import java.util.List;

public class SynchronizedBlockExample {
    public static void main(String[] args) throws InterruptedException {
        Stats stats = new Stats();
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            int id = t;
            threads.add(new Thread(() -> {
                for (int i = 0; i < 10_000; i++) {
                    stats.recordRequest("user-" + id);
                    if (i % 10 == 0) {
                        stats.recordError();
                    }
                }
            }));
        }
        threads.forEach(Thread::start);
        for (Thread t : threads) {
            t.join();
        }
        System.out.println("requests: " + stats.requests());
        System.out.println("errors:   " + stats.errors());
        System.out.println("bytes of log text built outside the lock: " + stats.logBytes());
    }
}

class Stats {
    // Private lock objects: nobody outside this class can lock them
    private final Object requestLock = new Object();
    private final Object errorLock = new Object();

    private long requests;
    private long logBytes;
    private long errors;

    void recordRequest(String user) {
        // Thread-confined work: no lock needed while building the message
        String line = "request from " + user;

        synchronized (requestLock) { // only the shared updates are guarded
            requests++;
            logBytes += line.length();
        }
    }

    void recordError() {
        // A different lock: error counting never waits for request counting
        synchronized (errorLock) {
            errors++;
        }
    }

    long requests() {
        synchronized (requestLock) {
            return requests;
        }
    }

    long logBytes() {
        synchronized (requestLock) {
            return logBytes;
        }
    }

    long errors() {
        synchronized (errorLock) {
            return errors;
        }
    }
}

/* Output:
requests: 40000
errors:   4000
bytes of log text built outside the lock: 760000
*/
