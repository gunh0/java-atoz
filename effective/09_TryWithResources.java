// Effective Java, Item 9: Prefer try-with-resources to try-finally.
//
// try-finally gets ugly with more than one resource (nested blocks), and it
// has a real flaw: if both the body and close() throw, the exception from
// close() replaces the first one, so the root cause disappears from the stack
// trace. try-with-resources (for any AutoCloseable) closes resources in
// reverse order, is shorter, and keeps the first exception while attaching
// the close() failures as suppressed exceptions.
public class TryWithResourcesDemo {
    public static void main(String[] args) {
        System.out.println("== try-finally ==");
        try {
            readWithTryFinally();
        } catch (Exception e) {
            System.out.println("caught: " + e.getMessage());
            System.out.println("suppressed: " + e.getSuppressed().length);
        }

        System.out.println("== try-with-resources ==");
        try {
            readWithTryWithResources();
        } catch (Exception e) {
            System.out.println("caught: " + e.getMessage());
            for (Throwable s : e.getSuppressed()) {
                System.out.println("suppressed: " + s.getMessage());
            }
        }
    }

    // Anti-pattern: nested try-finally, and the close() failure masks the read failure
    static void readWithTryFinally() throws Exception {
        FaultyResource in = new FaultyResource("in");
        try {
            FaultyResource out = new FaultyResource("out");
            try {
                in.read();
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    // Recommended: resources are closed automatically, out first, then in
    static void readWithTryWithResources() throws Exception {
        try (FaultyResource in = new FaultyResource("in");
             FaultyResource out = new FaultyResource("out")) {
            in.read();
        }
    }
}

// A resource whose read() and close() both fail, like a broken device
class FaultyResource implements AutoCloseable {
    private final String name;

    FaultyResource(String name) {
        this.name = name;
        System.out.println("open " + name);
    }

    void read() throws Exception {
        throw new Exception("read failed on " + name + " (root cause)");
    }

    @Override
    public void close() throws Exception {
        System.out.println("close " + name);
        throw new Exception("close failed on " + name);
    }
}

/* Output:
== try-finally ==
open in
open out
close out
close in
caught: close failed on in
suppressed: 0
== try-with-resources ==
open in
open out
close out
close in
caught: read failed on in (root cause)
suppressed: close failed on out
suppressed: close failed on in
*/
