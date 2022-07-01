// Every thread has a priority from Thread.MIN_PRIORITY (1) to
// Thread.MAX_PRIORITY (10); the default is Thread.NORM_PRIORITY (5).
//  - A new thread inherits the priority of the thread that creates it.
//  - setPriority() outside 1..10 throws IllegalArgumentException, and the
//    value is silently capped at the maximum priority of the thread's group.
//  - Priority is only a HINT to the OS scheduler. On Linux the JVM ignores it
//    by default, so never rely on it for correctness or ordering; use joins,
//    locks or java.util.concurrent instead. This example therefore prints the
//    priority values, not which thread "ran first".
public class ThreadPriorityExample {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("MIN=" + Thread.MIN_PRIORITY + " NORM=" + Thread.NORM_PRIORITY
                + " MAX=" + Thread.MAX_PRIORITY);
        System.out.println("main thread priority: " + Thread.currentThread().getPriority());

        // Setting priorities before start()
        Thread indexer = new Thread(() -> report(), "indexer");
        Thread ui = new Thread(() -> report(), "ui");
        indexer.setPriority(Thread.MIN_PRIORITY);
        ui.setPriority(Thread.MAX_PRIORITY);
        startAndJoin(indexer);
        startAndJoin(ui);

        // Inheritance: a thread created by a priority-8 thread starts at 8
        Thread parent = new Thread(() -> {
            Thread child = new Thread(() -> report(), "child");
            System.out.println("child created by parent starts with priority " + child.getPriority());
            startAndJoin(child);
        }, "parent");
        parent.setPriority(8);
        startAndJoin(parent);

        // Out-of-range values are rejected
        try {
            new Thread(() -> {}).setPriority(11);
        } catch (IllegalArgumentException e) {
            System.out.println("setPriority(11) -> IllegalArgumentException");
        }

        // The thread group's maximum caps what setPriority can set
        ThreadGroup background = new ThreadGroup("background");
        background.setMaxPriority(3);
        Thread batch = new Thread(background, () -> report(), "batch");
        batch.setPriority(Thread.MAX_PRIORITY); // asks for 10, gets 3
        startAndJoin(batch);
    }

    private static void report() {
        Thread t = Thread.currentThread();
        System.out.println(t.getName() + " running with priority " + t.getPriority());
    }

    // join() right after start() keeps the output order deterministic
    private static void startAndJoin(Thread t) {
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

/* Output:
MIN=1 NORM=5 MAX=10
main thread priority: 5
indexer running with priority 1
ui running with priority 10
child created by parent starts with priority 8
child running with priority 8
setPriority(11) -> IllegalArgumentException
batch running with priority 3
*/
