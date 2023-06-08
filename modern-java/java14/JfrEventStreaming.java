// JEP 349 — JFR Event Streaming (Java 14)
//
// JDK Flight Recorder records low-overhead events (GC, locks, I/O, custom
// application events), but before Java 14 you could only look at them
// after the fact: start a recording, stop it, dump a .jfr file, parse it.
// That ruled JFR out for continuous monitoring. JEP 349 adds
// jdk.jfr.consumer.RecordingStream, which subscribes to events while they
// are being recorded, in-process (or from another JVM's repository). Here
// the application defines its own event type, commits a few events, and
// a stream in the same JVM reacts to them, a building block for in-process
// security auditing or alerting.
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.consumer.RecordingStream;

public class JfrEventStreaming {
    @Name("demo.LoginAttempt")
    @Label("Login Attempt")
    @Category("Security")
    static class LoginAttempt extends Event {
        @Label("User")
        String user;
        @Label("Success")
        boolean success;
    }

    public static void main(String[] args) throws Exception {
        // Before: record, stop, dump to a file, then parse it afterwards
        Path dir = Files.createTempDirectory("jfr");
        Path file = dir.resolve("logins.jfr");
        try (Recording recording = new Recording()) {
            recording.enable("demo.LoginAttempt");
            recording.start();
            login("eve", false);
            login("carol", true);
            recording.stop();
            recording.dump(file);
        }
        long failed = RecordingFile.readAllEvents(file).stream()
                .filter(e -> e.getEventType().getName().equals("demo.LoginAttempt"))
                .filter(e -> !e.getBoolean("success"))
                .count();
        System.out.println("before: failed logins found in the dump afterwards = " + failed);
        Files.delete(file);
        Files.delete(dir);

        // After: react to events while they are recorded
        List<String> alerts = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch seen = new CountDownLatch(4);

        try (RecordingStream stream = new RecordingStream()) {
            stream.enable("demo.LoginAttempt");
            // React to each event while the recording is running
            stream.onEvent("demo.LoginAttempt", event -> {
                if (!event.getBoolean("success")) {
                    alerts.add("failed login for " + event.getString("user"));
                }
                seen.countDown();
            });
            stream.startAsync();

            login("alice", true);
            login("mallory", false);
            login("mallory", false);
            login("bob", true);

            // Events arrive after the next flush (about once per second)
            boolean all = seen.await(10, TimeUnit.SECONDS);
            System.out.println("after: all 4 events streamed live = " + all);
        }
        System.out.println("after: alerts " + alerts);
    }

    static void login(String user, boolean success) {
        LoginAttempt event = new LoginAttempt();
        event.user = user;
        event.success = success;
        event.commit(); // cheap no-op when no recording has the event enabled
    }
}

/* Output:
before: failed logins found in the dump afterwards = 1
after: all 4 events streamed live = true
after: alerts [failed login for mallory, failed login for mallory]
*/
