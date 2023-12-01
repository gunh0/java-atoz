// MSC05-J. Do not exhaust heap space
// https://wiki.sei.cmu.edu/confluence/display/java/MSC05-J.+Do+not+exhaust+heap+space
//
// Reading untrusted input "until the end" with readLine(), readAllBytes()
// or a growing List lets the sender decide how much memory the server
// allocates: one endless line or a few million tiny records and the JVM
// dies with OutOfMemoryError, taking every other request with it. Put an
// explicit bound on everything that grows with input: the length of a
// line, the number of records, the total number of bytes, and reject the
// request once a bound is reached. The demo feeds a generated 8 MB line
// (no newline) and measures how much each reader buffered.
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class MSC05_HeapExhaustion {
    static final int MAX_LINE = 1_024;
    static final int INPUT_SIZE = 8 * 1024 * 1024;

    public static void main(String[] args) throws IOException {
        String line = readLineNoncompliant(reader(hostileInput()));
        System.out.println("noncompliant: buffered a single line of " + line.length() / (1024 * 1024) + " MB");

        try {
            readLineCompliant(reader(hostileInput()), MAX_LINE);
        } catch (IOException e) {
            System.out.println("compliant:    " + e.getMessage());
        }
        System.out.println("compliant, normal input: " + readLineCompliant(reader(stream("hello\nworld\n")), MAX_LINE));
    }

    // Noncompliant: readLine() keeps buffering until it sees a newline;
    // with gigabytes instead of 8 MB this ends in OutOfMemoryError
    static String readLineNoncompliant(BufferedReader in) throws IOException {
        return in.readLine();
    }

    // Compliant: stops reading once the line exceeds the limit
    static String readLineCompliant(Reader in, int max) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1 && c != '\n') {
            if (sb.length() >= max) {
                throw new IOException("line longer than " + max + " characters rejected");
            }
            sb.append((char) c);
        }
        return sb.toString();
    }

    static BufferedReader reader(InputStream in) {
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    // An attacker's stream: INPUT_SIZE bytes of 'A' and no newline
    static InputStream hostileInput() {
        return new InputStream() {
            private int remaining = INPUT_SIZE;

            @Override
            public int read() {
                return remaining-- > 0 ? 'A' : -1;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                if (remaining <= 0) {
                    return -1;
                }
                int n = Math.min(len, remaining);
                Arrays.fill(b, off, off + n, (byte) 'A');
                remaining -= n;
                return n;
            }
        };
    }

    static InputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
}

/* Output:
noncompliant: buffered a single line of 8 MB
compliant:    line longer than 1024 characters rejected
compliant, normal input: hello
*/
