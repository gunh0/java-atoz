// FIO04-J. Release resources when they are no longer needed
// https://wiki.sei.cmu.edu/confluence/display/java/FIO04-J.+Release+resources+when+they+are+no+longer+needed
//
// The garbage collector reclaims memory, not file descriptors, sockets,
// database connections or locks, and it gives no guarantee about when (or
// whether) a finalizer or cleaner closes them. Code that closes a resource
// only on the success path leaks it every time an exception is thrown in
// between, and an attacker who can trigger that exception repeatedly can
// exhaust the process's descriptor or connection pool. Use
// try-with-resources (or close in finally) for everything that is
// AutoCloseable.
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

public class FIO04_ReleaseResources {
    static final AtomicInteger OPEN = new AtomicInteger();

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("fio04");
        Path good = Files.writeString(dir.resolve("good.csv"), "id,amount\n1,10\n");
        Path bad = Files.writeString(dir.resolve("bad.csv"), "id,amount\n1,ten\n");
        try {
            for (int i = 0; i < 100; i++) {
                parseNoncompliant(i % 2 == 0 ? good : bad);
            }
            System.out.println("noncompliant: 100 requests, readers still open: " + OPEN.get());

            OPEN.set(0);
            for (int i = 0; i < 100; i++) {
                parseCompliant(i % 2 == 0 ? good : bad);
            }
            System.out.println("compliant:    100 requests, readers still open: " + OPEN.get());
        } finally {
            Files.delete(good);
            Files.delete(bad);
            Files.delete(dir);
        }
    }

    // Noncompliant: close() is skipped when parsing throws
    static int parseNoncompliant(Path file) throws IOException {
        BufferedReader reader = open(file);
        try {
            int sum = sum(reader);
            reader.close();
            return sum;
        } catch (NumberFormatException e) {
            return -1; // bad input reported, reader leaked
        }
    }

    // Compliant: try-with-resources closes on every path
    static int parseCompliant(Path file) throws IOException {
        try (BufferedReader reader = open(file)) {
            return sum(reader);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static int sum(BufferedReader reader) throws IOException {
        reader.readLine(); // header
        int sum = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            sum += Integer.parseInt(line.split(",")[1]);
        }
        return sum;
    }

    // Opens a real file and counts how many readers are open at the moment
    static BufferedReader open(Path file) throws IOException {
        Reader r = Files.newBufferedReader(file);
        OPEN.incrementAndGet();
        return new BufferedReader(r) {
            private boolean closed;

            @Override
            public void close() throws IOException {
                if (!closed) {
                    closed = true;
                    OPEN.decrementAndGet();
                }
                super.close();
            }
        };
    }
}

/* Output:
noncompliant: 100 requests, readers still open: 50
compliant:    100 requests, readers still open: 0
*/
