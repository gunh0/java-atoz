// THI05-J. Do not use Thread.stop() to terminate threads
// https://wiki.sei.cmu.edu/confluence/display/java/THI05-J.+Do+not+use+Thread.stop()+to+terminate+threads
//
// Thread.stop() made the target thread throw ThreadDeath at whatever
// instruction it happened to be executing, releasing every monitor it held.
// Any object it was halfway through updating was left inconsistent and
// immediately visible to other threads (for example a transfer that had
// debited one account but not yet credited the other). The method has been
// deprecated since Java 1.2, and since Java 20 it no longer stops anything:
// it just throws UnsupportedOperationException; JDK 26 removed the method
// altogether, so the demo looks it up reflectively. Stop threads cooperatively:
// interrupt() them (or set a volatile flag) and let the thread check for
// it at points where its state is consistent, then clean up and return.
import java.util.concurrent.CountDownLatch;

public class THI05_ThreadStop {
    public static void main(String[] args) throws InterruptedException {
        // Noncompliant: relies on Thread.stop()
        Worker noncompliant = new Worker();
        Thread t1 = new Thread(noncompliant);
        t1.start();
        noncompliant.started.await();
        boolean stopped = stopNoncompliant(t1);
        System.out.println("noncompliant: Thread.stop() stopped the thread: " + stopped + ", thread still alive: " + t1.isAlive());
        t1.interrupt(); // clean up the demo thread
        t1.join();

        // Compliant: interrupt and let the worker finish its current unit of work
        Worker compliant = new Worker();
        Thread t2 = new Thread(compliant);
        t2.start();
        compliant.started.await();
        t2.interrupt();
        t2.join();
        System.out.println("compliant:    worker stopped cleanly, balances consistent: " + compliant.consistent());
    }

    // Java 20-25: stop() throws UnsupportedOperationException; Java 26+: no such method.
    static boolean stopNoncompliant(Thread t) {
        try {
            Thread.class.getMethod("stop").invoke(t);
            return true;
        } catch (java.lang.reflect.InvocationTargetException e) {
            return !(e.getCause() instanceof UnsupportedOperationException);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}

class Worker implements Runnable {
    final CountDownLatch started = new CountDownLatch(1);
    private final Object lock = new Object();
    private int a = 1_000, b = 0;

    @Override
    public void run() {
        started.countDown();
        // Each iteration is one complete transfer; the interrupt is checked between transfers
        while (!Thread.currentThread().isInterrupted()) {
            synchronized (lock) {
                a--;
                b++;
            }
            Thread.onSpinWait();
        }
    }

    boolean consistent() {
        synchronized (lock) {
            return a + b == 1_000;
        }
    }
}

/* Output:
noncompliant: Thread.stop() stopped the thread: false, thread still alive: true
compliant:    worker stopped cleanly, balances consistent: true
*/
