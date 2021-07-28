// Thread.sleep(millis) pauses the CURRENT thread for at least the given time.
//  - It is a static method: t.sleep(100) would still sleep the caller, not t.
//  - The real pause can be longer (scheduler and timer granularity), never
//    intentionally shorter, so it is not a precise clock.
//  - A sleeping thread keeps every lock (monitor) it holds.
//  - It throws the checked InterruptedException if another thread interrupts it.
//  - A negative duration throws IllegalArgumentException.
import java.util.concurrent.CountDownLatch;

public class ThreadSleepExample {
    public static void main(String[] args) throws InterruptedException {
        // Sleep the main thread and measure with the monotonic clock
        long start = System.nanoTime();
        Thread.sleep(200);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        System.out.println("main slept at least 200 ms: " + (elapsedMillis >= 200));

        // sleep() inside a worker: count down with a pause between steps
        Thread countdown = new Thread(() -> {
            for (int i = 3; i >= 1; i--) {
                System.out.println("countdown " + i);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // keep the interrupt status
                    return;
                }
            }
            System.out.println("liftoff");
        }, "countdown");
        countdown.start();
        countdown.join();

        // A sleeping thread does not release its locks
        Object lock = new Object();
        CountDownLatch locked = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            synchronized (lock) {
                locked.countDown(); // tell main that the lock is taken
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        holder.start();
        locked.await();
        long waitStart = System.nanoTime();
        synchronized (lock) {
            long waited = (System.nanoTime() - waitStart) / 1_000_000;
            System.out.println("main had to wait for the sleeping holder: " + (waited >= 100));
        }
        holder.join();

        try {
            Thread.sleep(-1);
        } catch (IllegalArgumentException e) {
            System.out.println("sleep(-1): " + e.getMessage());
        }
    }
}

/* Output:
main slept at least 200 ms: true
countdown 3
countdown 2
countdown 1
liftoff
main had to wait for the sleeping holder: true
sleep(-1): timeout value is negative
*/
