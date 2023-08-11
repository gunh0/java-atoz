// Buffered streams wrap another stream and move data in large blocks
// (8 KiB by default) instead of one system call per byte or char.
//  - BufferedWriter.newLine() / BufferedReader.readLine() work line by line;
//    readLine() strips the line terminator and returns null at the end
//  - BufferedReader.lines() exposes the lines as a Stream<String>
//  - a buffered writer holds data in memory until flush() or close(): forget
//    to close it and the tail of the file is silently lost
// The classic decorator chain: new BufferedReader(new InputStreamReader(
// new FileInputStream(f), UTF_8)); Files.newBufferedReader builds it for you.
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

public class BufferedStreamsExample {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("buffered");
        Path log = dir.resolve("auth.log");
        try {
            String[] users = {"alice", "bob", "root", "alice", "admin", "root", "root"};
            try (BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(log.toFile()), StandardCharsets.UTF_8))) {
                for (int i = 0; i < users.length; i++) {
                    out.write((i % 3 == 0 ? "ACCEPT" : "FAIL") + " ssh user=" + users[i]);
                    out.newLine();
                }
            }
            System.out.println("lines written: " + Files.readAllLines(log).size());

            // readLine loop: count failed logins per user
            Map<String, Integer> failures = new TreeMap<>();
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(new FileInputStream(log.toFile()), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("FAIL")) {
                        String user = line.substring(line.indexOf("user=") + 5);
                        failures.merge(user, 1, Integer::sum);
                    }
                }
            }
            System.out.println("failed logins: " + failures);

            // lines() as a stream, via the Files shortcut
            try (BufferedReader in = Files.newBufferedReader(log, StandardCharsets.UTF_8)) {
                System.out.println("accepted: " + in.lines().filter(l -> l.startsWith("ACCEPT")).map(l -> l.split("=")[1]).toList());
            }

            // Without close/flush, buffered data never reaches the file
            Path lost = dir.resolve("lost.log");
            BufferedWriter leaky = Files.newBufferedWriter(lost, StandardCharsets.UTF_8);
            leaky.write("this line sits in the buffer");
            System.out.println("before flush, file size: " + Files.size(lost));
            leaky.flush();
            System.out.println("after flush, file size:  " + Files.size(lost));
            leaky.close();
            Files.delete(lost);
        } finally {
            Files.deleteIfExists(log);
            Files.deleteIfExists(dir);
        }
    }
}

/* Output:
lines written: 7
failed logins: {admin=1, bob=1, root=2}
accepted: [alice, alice, root]
before flush, file size: 0
after flush, file size:  28
*/
