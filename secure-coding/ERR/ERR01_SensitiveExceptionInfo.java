// ERR01-J. Do not allow exceptions to expose sensitive information
// https://wiki.sei.cmu.edu/confluence/display/java/ERR01-J.+Do+not+allow+exceptions+to+expose+sensitive+information
//
// Exception messages and stack traces are written for developers. Passed on
// to a client, they reveal internals: absolute paths and user names, the
// layout of the file system, library versions, SQL text. They can also act
// as an oracle: "No such file or directory" versus "Is a directory" versus
// success tells an attacker which paths exist. Catch exceptions at the trust
// boundary, log the details server side, and give the caller a generic
// message. Better still, never let raw input choose the file at all: map it
// to a fixed set of allowed resources.
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ERR01_SensitiveExceptionInfo {
    static Path base;
    static final List<String> serverLog = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("err01");
        base = Files.createDirectories(root.resolve("public"));
        Path reports = Files.createDirectories(base.resolve("reports"));
        Path secrets = Files.createDirectories(root.resolve("secrets"));
        Files.writeString(reports.resolve("q1.txt"), "Q1 revenue: 42", StandardCharsets.UTF_8);
        Files.writeString(secrets.resolve("db.properties"), "db.password=hunter2", StandardCharsets.UTF_8);
        try {
            String[] requests = {"reports/q1.txt", "reports/q9.txt", "reports", "../secrets/db.properties"};
            for (String r : requests) {
                System.out.println("request " + r);
                System.out.println("  noncompliant -> " + mask(readNoncompliant(r)));
                System.out.println("  compliant    -> " + readCompliant(r));
            }
            System.out.println("server-side log entries (details kept internal): " + serverLog.size());
        } finally {
            Files.delete(reports.resolve("q1.txt"));
            Files.delete(reports);
            Files.delete(base);
            Files.delete(secrets.resolve("db.properties"));
            Files.delete(secrets);
            Files.delete(root);
        }
    }

    // Noncompliant: the exception message (with the full path) goes to the client,
    // and the different messages reveal what exists on the server
    static String readNoncompliant(String request) {
        try (InputStream in = new FileInputStream(base.resolve(request).toFile())) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "error: " + e.getMessage();
        }
    }

    // Compliant: input selects from a fixed set; failures get one generic message
    private static final Map<String, String> ALLOWED = Map.of("reports/q1.txt", "reports/q1.txt",
            "reports/q2.txt", "reports/q2.txt");

    static String readCompliant(String request) {
        String file = ALLOWED.get(request);
        if (file == null) {
            serverLog.add("rejected request: " + request);
            return "error: report not available";
        }
        try (InputStream in = new FileInputStream(base.resolve(file).toFile())) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            serverLog.add("read failed: " + e); // full detail only in the server log
            return "error: report not available";
        }
    }

    // The temp directory differs between runs; show it as <base>
    static String mask(String s) {
        return s.replace(base.toString(), "<base>");
    }
}

/* Output:
request reports/q1.txt
  noncompliant -> Q1 revenue: 42
  compliant    -> Q1 revenue: 42
request reports/q9.txt
  noncompliant -> error: <base>/reports/q9.txt (No such file or directory)
  compliant    -> error: report not available
request reports
  noncompliant -> error: <base>/reports (Is a directory)
  compliant    -> error: report not available
request ../secrets/db.properties
  noncompliant -> db.password=hunter2
  compliant    -> error: report not available
server-side log entries (details kept internal): 3
*/
