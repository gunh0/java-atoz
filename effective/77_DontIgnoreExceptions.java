// Effective Java, Item 77: Don't ignore exceptions.
//
// An empty catch block defeats the purpose of exceptions: the program keeps
// running with a failure nobody knows about, and the eventual symptom shows
// up far from its cause. At a minimum, handle the exception, propagate it,
// or record that it happened. There are rare cases where ignoring is right
// (closing a read-only stream, an optional best-effort cache warm-up); then
// name the variable `ignored` and write a comment saying why.
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class DontIgnoreExceptions {
    public static void main(String[] args) throws IOException {
        List<String> amounts = List.of("10", "25", "1O", "40", "5,0");

        // Anti-pattern: swallow and move on
        int total = 0;
        for (String a : amounts) {
            try {
                total += Integer.parseInt(a);
            } catch (NumberFormatException e) {
            }
        }
        System.out.println("swallowed: total = " + total + " (two bad records vanished)");

        // Better: record the failures and let the caller decide
        int checkedTotal = 0;
        List<String> rejected = new ArrayList<>();
        for (String a : amounts) {
            try {
                checkedTotal += Integer.parseInt(a);
            } catch (NumberFormatException e) {
                rejected.add(a);
            }
        }
        System.out.println("recorded:  total = " + checkedTotal + ", rejected = " + rejected);
        if (!rejected.isEmpty()) {
            System.out.println("           -> report " + rejected.size() + " bad records instead of a wrong sum");
        }

        // Legitimately ignored: closing an in-memory read-only stream
        InputStream in = new ByteArrayInputStream(new byte[] {1, 2, 3});
        int first = in.read();
        try {
            in.close();
        } catch (IOException ignored) {
            // Nothing was written and the data is already read; a close failure cannot lose anything
        }
        System.out.println("read " + first + ", close failure (if any) deliberately ignored");
    }
}

/* Output:
swallowed: total = 75 (two bad records vanished)
recorded:  total = 75, rejected = [1O, 5,0]
           -> report 2 bad records instead of a wrong sum
read 1, close failure (if any) deliberately ignored
*/
