// FileChannel.lock() / tryLock() take an OS-level lock on a file or a byte
// range of it. Uses: a single-instance guard ("app.lock"), or coordinating
// several processes that update the same file.
//  - the lock belongs to the whole JVM process, not to a thread: a second
//    overlapping lock attempt in the same JVM throws
//    OverlappingFileLockException instead of waiting
//  - tryLock() returns null when ANOTHER process holds a conflicting lock;
//    lock() would block until it is released
//  - shared locks (readers) can coexist; an exclusive lock (writer) cannot
//    coexist with any other lock on the same range
//  - on Linux and macOS locks are advisory: they only stop processes that
//    also ask for the lock, not a plain write
//  - closing the channel releases its locks. On some systems closing ANY
//    channel to the file drops every lock this JVM holds on it, so open
//    exactly one channel per file and take all locks through it
// The second process here is a tiny Java program the example writes to a
// temp directory and starts with the same java launcher.
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.stream.Stream;

public class FileLockExample {
    static final String OTHER_PROCESS = """
            import java.nio.ByteBuffer;
            import java.nio.channels.FileChannel;
            import java.nio.channels.FileLock;
            import java.nio.file.Path;
            import java.nio.file.StandardOpenOption;

            public class OtherProcess {
                public static void main(String[] args) throws Exception {
                    boolean shared = args[1].equals("shared");
                    try (FileChannel ch = FileChannel.open(Path.of(args[0]), StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                        if (args[1].equals("write")) {
                            ch.write(ByteBuffer.wrap("!".getBytes()), 0); // no lock requested at all
                            System.out.print("wrote without a lock");
                            return;
                        }
                        try (FileLock lock = ch.tryLock(0, Long.MAX_VALUE, shared)) {
                            System.out.print(lock == null ? "busy" : (shared ? "got shared lock" : "got exclusive lock"));
                        }
                    }
                }
            }
            """;

    static Path dir;

    static String otherProcess(Path file, String mode) throws IOException, InterruptedException {
        String java = ProcessHandle.current().info().command().orElse("java");
        Process p = new ProcessBuilder(java, dir.resolve("OtherProcess.java").toString(), file.toString(), mode)
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        p.waitFor();
        return out;
    }

    public static void main(String[] args) throws Exception {
        dir = Files.createTempDirectory("filelock");
        Files.writeString(dir.resolve("OtherProcess.java"), OTHER_PROCESS);
        Path file = Files.writeString(dir.resolve("app.lock"), "pid file");
        try {
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                FileLock lock = ch.tryLock();
                System.out.println("this process, exclusive tryLock: " + (lock != null) + ", shared: " + lock.isShared());

                try {
                    ch.tryLock(0, 4, true); // any overlap inside this JVM, even a shared one
                } catch (OverlappingFileLockException e) {
                    System.out.println("same JVM, overlapping range: OverlappingFileLockException");
                }

                System.out.println("other process, exclusive: " + otherProcess(file, "exclusive"));
                System.out.println("other process, shared:    " + otherProcess(file, "shared"));
                System.out.println("other process, no lock:   " + otherProcess(file, "write") + " (advisory)");

                lock.release();
                System.out.println("released, valid: " + lock.isValid());
                System.out.println("other process, exclusive: " + otherProcess(file, "exclusive"));

                // Shared lock: other readers may join, writers may not
                try (FileLock shared = ch.lock(0, Long.MAX_VALUE, true)) {
                    System.out.println("this process holds a shared lock");
                    System.out.println("other process, shared:    " + otherProcess(file, "shared"));
                    System.out.println("other process, exclusive: " + otherProcess(file, "exclusive"));
                }

                // Byte-range locks: disjoint regions do not overlap
                try (FileLock head = ch.lock(0, 4, false); FileLock tail = ch.lock(4, 4, false)) {
                    System.out.println("regions [0,4) and [4,8) locked together: " + (head.isValid() && tail.isValid())
                            + ", head overlaps [2,3): " + head.overlaps(2, 1));
                }
            } // closing the channel would also have released every lock

            ByteBuffer first = ByteBuffer.allocate(1);
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
                ch.read(first);
            }
            System.out.println("first byte of the file now: " + (char) first.get(0));
        } finally {
            try (Stream<Path> s = Files.walk(dir)) {
                for (Path p : s.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
        }
    }
}

/* Output:
this process, exclusive tryLock: true, shared: false
same JVM, overlapping range: OverlappingFileLockException
other process, exclusive: busy
other process, shared:    busy
other process, no lock:   wrote without a lock (advisory)
released, valid: false
other process, exclusive: got exclusive lock
this process holds a shared lock
other process, shared:    got shared lock
other process, exclusive: busy
regions [0,4) and [4,8) locked together: true, head overlaps [2,3): true
first byte of the file now: !
*/
