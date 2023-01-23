// `java File.java` runs the first class in the file, so the class with main() comes first.
public class ThreadJoinExample1 {
    // main method
    public static void main(String[] argvs) {
        try {
            // creating an object of the class ABC
            ABC th1 = new ABC();

            th1.threadToInterrupt = Thread.currentThread();
            th1.start();

            // invoking the join() method leads
            // to the generation of InterruptedException
            th1.join();
        } catch (InterruptedException ex) {
            System.out.println("The exception has been caught. " + ex);
        }
    }
}

class ABC extends Thread {
    Thread threadToInterrupt;

    // overriding the run() method
    public void run() {
        // invoking the method interrupt
        threadToInterrupt.interrupt();
        // Stay alive for a moment: as long as this thread is alive, main's
        // join() keeps waiting, so the interrupt surfaces as an
        // InterruptedException even if it arrived before join() was called.
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {
        }
    }
}

/* Output:
The exception has been caught. java.lang.InterruptedException
*/
