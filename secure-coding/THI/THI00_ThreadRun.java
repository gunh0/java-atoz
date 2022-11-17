// THI00-J. Do not invoke Thread.run()
// https://wiki.sei.cmu.edu/confluence/display/java/THI00-J.+Do+not+invoke+Thread.run()
//
// Thread.start() creates a new thread of execution, which then calls run().
// Calling run() directly is just an ordinary method call: the code runs in
// the CURRENT thread, synchronously. The program still "works" in tests, but
// the work is no longer concurrent, a slow task blocks the caller (for
// example a request handler or the UI thread), and anything that relies on
// the thread's own identity, name, priority, thread-locals or uncaught
// exception handler is silently wrong. Always call start(); if you do want
// to run the task inline, call the Runnable itself, not Thread.run().
import java.util.concurrent.atomic.AtomicReference;

public class THI00_ThreadRun {
    public static void main(String[] args) throws InterruptedException {
        AtomicReference<String> ranOn = new AtomicReference<>();
        Runnable task = () -> ranOn.set(Thread.currentThread().getName());

        // Noncompliant: run() executes in the caller's thread
        Thread worker = new Thread(task, "worker-1");
        worker.run();
        System.out.println("noncompliant run():   task ran on \"" + ranOn.get() + "\", worker state " + worker.getState());

        // Compliant: start() creates the thread, which then calls run()
        Thread worker2 = new Thread(task, "worker-2");
        worker2.start();
        worker2.join();
        System.out.println("compliant start():    task ran on \"" + ranOn.get() + "\", worker state " + worker2.getState());

        // The uncaught exception handler only applies to a started thread
        Runnable failingTask = () -> {
            throw new IllegalStateException("task failed");
        };
        Thread.UncaughtExceptionHandler handler = (t, e) ->
                System.out.println("compliant start():    handler on \"" + t.getName() + "\" caught: " + e.getMessage());

        Thread failing = new Thread(failingTask, "worker-3");
        failing.setUncaughtExceptionHandler(handler);
        try {
            failing.run();
        } catch (IllegalStateException e) {
            System.out.println("noncompliant run():   exception escaped into \"" + Thread.currentThread().getName()
                    + "\", handler skipped");
        }

        Thread failing2 = new Thread(failingTask, "worker-4");
        failing2.setUncaughtExceptionHandler(handler);
        failing2.start();
        failing2.join();
    }
}

/* Output:
noncompliant run():   task ran on "main", worker state NEW
compliant start():    task ran on "worker-2", worker state TERMINATED
noncompliant run():   exception escaped into "main", handler skipped
compliant start():    handler on "worker-4" caught: task failed
*/
