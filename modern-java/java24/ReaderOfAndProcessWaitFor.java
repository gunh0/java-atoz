// Reader.of(CharSequence) and Process.waitFor(Duration) (Java 24)
//
// Two small API additions in Java 24, found by comparing the public APIs of
// Java 23 and 24.
// Reader.of(CharSequence): to read text that is not a String (a
// StringBuilder, a CharBuffer, a custom CharSequence) through a Reader, you
// had to write new StringReader(sb.toString()), copying the whole content
// first. Reader.of reads the sequence in place. The reader supports mark and
// reset, is not thread-safe (unlike StringReader, it takes no lock) and
// throws IOException after close(), like other readers.
// Process.waitFor(Duration): waiting with a timeout used to be
// waitFor(long, TimeUnit); the Duration overload matches the rest of the
// modern time-based APIs (Thread.sleep(Duration), Thread.join(Duration),
// both Java 19). It returns false when the time runs out, leaving the
// process running, so the caller decides whether to destroy it.
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class ReaderOfAndProcessWaitFor {
    public static void main(String[] args) throws Exception {
        StringBuilder log = new StringBuilder();
        for (int i = 1; i <= 3; i++) log.append("line ").append(i).append('\n');

        // Before: copy to a String first
        String viaCopy = read(new StringReader(log.toString()));
        // After: read the StringBuilder directly
        String viaReaderOf = read(Reader.of(log));
        System.out.println("same text: " + viaCopy.equals(viaReaderOf) + ", " + viaReaderOf.lines().count() + " lines");

        // Any CharSequence works, e.g. a CharBuffer over a char[]
        char[] secret = {'s', '3', 'c', 'r', '3', 't'};
        System.out.println("CharBuffer over char[]: " + read(Reader.of(CharBuffer.wrap(secret))).length() + " chars read in place");

        // mark/reset, skip, and close behave like other readers
        Reader r = Reader.of("abcdef");
        r.skip(2);
        r.mark(10);
        char[] two = new char[2];
        r.read(two);
        r.reset();
        System.out.println("markSupported " + r.markSupported() + ", after skip(2) read \"" + new String(two)
                + "\", after reset read '" + (char) r.read() + "'");
        r.close();
        try {
            r.read();
        } catch (IOException e) {
            System.out.println("read after close: IOException");
        }

        // Process.waitFor(Duration)
        Path dir = Files.createTempDirectory("waitfor");
        Path sleeper = dir.resolve("Sleeper.java");
        Files.writeString(sleeper, "class Sleeper { public static void main(String[] a) throws Exception { Thread.sleep(30_000); } }");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        try {
            Process quick = new ProcessBuilder(java, "-version").redirectErrorStream(true).start();
            quick.getInputStream().readAllBytes();
            System.out.println("java -version finished within 30 s: " + quick.waitFor(Duration.ofSeconds(30))
                    + ", exit " + quick.exitValue());

            Process slow = new ProcessBuilder(java, sleeper.toString()).start();
            boolean done = slow.waitFor(Duration.ofMillis(300));
            System.out.println("sleeper finished within 300 ms: " + done + ", still alive: " + slow.isAlive());
            slow.destroy();
            // The old overload, for comparison
            System.out.println("after destroy(), finished within 10 s: " + slow.waitFor(10, TimeUnit.SECONDS));
        } finally {
            Files.delete(sleeper);
            Files.delete(dir);
        }
    }

    static String read(Reader reader) throws IOException {
        try (reader) {
            StringWriter out = new StringWriter();
            reader.transferTo(out);
            return out.toString();
        }
    }
}

/* Output:
same text: true, 3 lines
CharBuffer over char[]: 6 chars read in place
markSupported true, after skip(2) read "cd", after reset read 'c'
read after close: IOException
java -version finished within 30 s: true, exit 0
sleeper finished within 300 ms: false, still alive: true
after destroy(), finished within 10 s: true
*/
