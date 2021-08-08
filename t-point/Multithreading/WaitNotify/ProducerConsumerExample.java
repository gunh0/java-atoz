// wait() and notify()/notifyAll() let threads coordinate on a shared object.
//  - Both must be called while holding that object's monitor (inside
//    synchronized), otherwise IllegalMonitorStateException is thrown.
//  - wait() releases the monitor and suspends the thread until another thread
//    calls notify/notifyAll on the same object; it re-acquires the monitor
//    before returning.
//  - Always wait in a while loop that re-checks the condition: a thread can
//    wake up spuriously or find that another thread consumed the item first.
// Classic use: a bounded buffer between a producer and a consumer. (In real
// code, prefer java.util.concurrent.BlockingQueue, which does all of this.)
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class ProducerConsumerExample {
    public static void main(String[] args) throws InterruptedException {
        BoundedBuffer buffer = new BoundedBuffer(3);
        List<Integer> received = new ArrayList<>();

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 10; i++) {
                    buffer.put(i);
                }
                buffer.put(-1); // poison pill: tells the consumer to stop
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        Thread consumer = new Thread(() -> {
            try {
                while (true) {
                    int item = buffer.take();
                    if (item == -1) {
                        break;
                    }
                    received.add(item * item);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join(); // join also makes `received` safely visible to main

        // FIFO buffer + one consumer -> the order is deterministic
        System.out.println("squares received: " + received);
        System.out.println("never more than 3 items buffered: " + (buffer.maxSeen() <= 3));

        try {
            new Object().wait();
        } catch (IllegalMonitorStateException e) {
            System.out.println("wait() without the lock: IllegalMonitorStateException");
        }
    }
}

class BoundedBuffer {
    private final Queue<Integer> items = new ArrayDeque<>();
    private final int capacity;
    private int maxSeen;

    BoundedBuffer(int capacity) {
        this.capacity = capacity;
    }

    synchronized void put(int item) throws InterruptedException {
        while (items.size() == capacity) {
            wait();                 // buffer full: release the lock and wait
        }
        items.add(item);
        maxSeen = Math.max(maxSeen, items.size());
        notifyAll();                // wake a consumer waiting for an item
    }

    synchronized int take() throws InterruptedException {
        while (items.isEmpty()) {
            wait();                 // buffer empty: release the lock and wait
        }
        int item = items.remove();
        notifyAll();                // wake a producer waiting for space
        return item;
    }

    synchronized int maxSeen() {
        return maxSeen;
    }
}

/* Output:
squares received: [1, 4, 9, 16, 25, 36, 49, 64, 81, 100]
never more than 3 items buffered: true
wait() without the lock: IllegalMonitorStateException
*/
