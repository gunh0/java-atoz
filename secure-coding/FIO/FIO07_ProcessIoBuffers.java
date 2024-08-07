// FIO07-J. Do not let external processes block on IO buffers
// https://wiki.sei.cmu.edu/confluence/display/java/FIO07-J.+Do+not+let+external+processes+block+on+IO+buffers
//
// A child process started with ProcessBuilder or Runtime.exec writes its
// stdout and stderr into pipes, and pipes hold only a small buffer (often
// 64 KiB). If the parent calls waitFor() without reading them, a child that
// produces more output than that blocks forever on write, and the parent
// blocks forever waiting: a deadlock that only appears with real-world
// output sizes (a verbose error, a big report), and a cheap way to hang a
// service. Always consume or redirect both streams: merge them with
// redirectErrorStream(true) and read, redirect to a file, or discard with
// Redirect.DISCARD.
import java.io.IOException;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FIO07_ProcessIoBuffers {
    // The child writes about 1 MiB to stderr, far more than a pipe buffer
    static final String CHILD = """
            public class Chatty {
                public static void main(String[] args) {
                    String line = "x".repeat(1023);
                    for (int i = 0; i < 1024; i++) {
                        System.err.println(line);
                    }
                    System.out.println("done");
                }
            }
            """;

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("fio07");
        Path child = dir.resolve("Chatty.java");
        Files.writeString(child, CHILD);
        try {
            // Noncompliant: nobody reads stderr
            Process p = start(dir);
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            System.out.println("noncompliant: child finished within 5 s: " + finished);
            if (!finished) {
                p.destroyForcibly().waitFor();
                System.out.println("noncompliant: had to kill the blocked child");
            }

            // Compliant 1: merge stderr into stdout and drain it
            ProcessBuilder merged = builder(dir).redirectErrorStream(true);
            Process p2 = merged.start();
            long bytes;
            try (InputStream in = p2.getInputStream()) {
                bytes = in.transferTo(OutputStreamSink.INSTANCE);
            }
            System.out.println("compliant merged+drained: exit " + p2.waitFor() + ", read " + (bytes / 1024) + " KiB");

            // Compliant 2: send what we do not need to the bit bucket
            Process p3 = builder(dir).redirectError(Redirect.DISCARD).start();
            String out = new String(p3.getInputStream().readAllBytes()).strip();
            System.out.println("compliant stderr discarded: exit " + p3.waitFor() + ", stdout \"" + out + "\"");
        } finally {
            Files.delete(child);
            Files.delete(dir);
        }
    }

    static ProcessBuilder builder(Path dir) {
        String java = ProcessHandle.current().info().command().orElse("java");
        return new ProcessBuilder(List.of(java, "Chatty.java")).directory(dir.toFile());
    }

    static Process start(Path dir) throws IOException {
        return builder(dir).start();
    }
}

// Counts and drops bytes
class OutputStreamSink extends java.io.OutputStream {
    static final OutputStreamSink INSTANCE = new OutputStreamSink();

    @Override
    public void write(int b) {
    }

    @Override
    public void write(byte[] b, int off, int len) {
    }
}

/* Output:
noncompliant: child finished within 5 s: false
noncompliant: had to kill the blocked child
compliant merged+drained: exit 0, read 1024 KiB
compliant stderr discarded: exit 0, stdout "done"
*/
