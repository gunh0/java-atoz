// A daemon thread is a background service thread (like the garbage collector).
// The JVM exits as soon as every NON-daemon thread has finished: remaining
// daemon threads are abandoned mid-work, without running their finally blocks.
//  - setDaemon(true) must be called before start(), otherwise it throws
//    IllegalThreadStateException.
//  - A new thread inherits the daemon status of the thread that creates it.
// Use daemons only for work that may be dropped at any moment (housekeeping,
// monitoring), never for work that must complete, such as writing a file.
public class DaemonThreadExample {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("main is daemon: " + Thread.currentThread().isDaemon());

        // A background "heartbeat" that would run forever
        Thread heartbeat = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "heartbeat");
        heartbeat.setDaemon(true);
        heartbeat.start();
        System.out.println("heartbeat is daemon: " + heartbeat.isDaemon());

        // The daemon flag is inherited from the creating thread
        Thread[] child = new Thread[1];
        Thread parent = new Thread(() -> child[0] = new Thread(() -> { }));
        parent.setDaemon(true);
        parent.start();
        parent.join();
        System.out.println("thread created by a daemon is daemon: " + child[0].isDaemon());
        System.out.println("thread created by main is daemon: " + new Thread(() -> { }).isDaemon());

        // Too late once the thread is running
        Thread running = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        });
        running.start();
        try {
            running.setDaemon(true);
        } catch (IllegalThreadStateException e) {
            System.out.println("setDaemon after start(): IllegalThreadStateException");
        }
        running.join();

        // main returns here; the JVM does not wait for the endless heartbeat
        System.out.println("main finished, heartbeat still alive: " + heartbeat.isAlive());
    }
}

/* Output:
main is daemon: false
heartbeat is daemon: true
thread created by a daemon is daemon: true
thread created by main is daemon: false
setDaemon after start(): IllegalThreadStateException
main finished, heartbeat still alive: true
*/
