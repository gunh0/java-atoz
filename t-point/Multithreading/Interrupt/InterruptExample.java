// interrupt() is Java's cooperative way to ask a thread to stop what it is
// doing. It does not kill the thread; it sets the thread's interrupt flag.
//  - If the thread is blocked in sleep(), wait() or join(), that call throws
//    InterruptedException and the flag is CLEARED.
//  - Otherwise the thread must check the flag itself with isInterrupted()
//    (does not clear) or the static Thread.interrupted() (clears it).
//  - If you catch InterruptedException and cannot rethrow it, restore the
//    flag with Thread.currentThread().interrupt() so callers can still see it.
public class InterruptExample {
    public static void main(String[] args) throws InterruptedException {
        // 1. Interrupting a sleeping thread
        Thread sleeper = new Thread(() -> {
            try {
                Thread.sleep(10_000);
                System.out.println("sleeper: woke up normally");
            } catch (InterruptedException e) {
                System.out.println("sleeper: interrupted, flag now "
                        + Thread.currentThread().isInterrupted());
            }
        });
        sleeper.start();
        sleeper.interrupt(); // even if it is not asleep yet, sleep() will throw at once
        sleeper.join();

        // 2. A busy thread must poll the flag
        Thread worker = new Thread(() -> {
            double work = 0;
            while (!Thread.currentThread().isInterrupted()) {
                work += Math.sqrt(work + 1); // pretend to work, never blocking
            }
            System.out.println("worker: saw the flag and stopped cleanly");
        });
        worker.start();
        worker.interrupt();
        worker.join();

        // 3. Restoring the flag so that callers further up can react
        Thread layered = new Thread(() -> {
            quietPause();
            System.out.println("layered: caller still sees interrupt: "
                    + Thread.currentThread().isInterrupted());
        });
        layered.start();
        layered.interrupt();
        layered.join();

        // 4. interrupted() reads AND clears the flag of the current thread
        Thread.currentThread().interrupt();
        System.out.println("main: interrupted() first call: " + Thread.interrupted());
        System.out.println("main: interrupted() second call: " + Thread.interrupted());
    }

    // A helper that cannot throw InterruptedException (e.g. it implements an
    // interface method without `throws`), so it re-asserts the interrupt
    private static void quietPause() {
        try {
            Thread.sleep(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

/* Output:
sleeper: interrupted, flag now false
worker: saw the flag and stopped cleanly
layered: caller still sees interrupt: true
main: interrupted() first call: true
main: interrupted() second call: false
*/
