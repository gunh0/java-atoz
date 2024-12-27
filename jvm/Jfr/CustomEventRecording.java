// JDK Flight Recorder: record custom events in-process and read them back
//
// JFR is the JVM's built-in, always-available event recorder: hundreds of
// JDK events (GC pauses, lock contention, allocation samples, socket and
// file I/O, class loading) plus your own events, written to thread-local
// buffers with overhead low enough for production. An application event is
// a subclass of jdk.jfr.Event with annotated fields; begin()/end() measure a
// duration, commit() writes it if the event type is enabled and passes its
// threshold. Recordings can be started from the command line
// (-XX:StartFlightRecording), with jcmd, or, as here, with the
// jdk.jfr.Recording API, then dumped to a .jfr file and parsed with
// jdk.jfr.consumer.RecordingFile (or opened in JDK Mission Control). For
// reacting to events live, see modern-java/java14/JfrEventStreaming.java.
// Field values are printed; timestamps and durations are only checked.
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import jdk.jfr.Category;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.StackTrace;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

public class CustomEventRecording {
    @Name("atoz.Login")
    @Label("Login attempt")
    @Category({"Java A to Z", "Security"})
    @StackTrace(false) // no stack trace needed: cheaper
    static class LoginEvent extends Event {
        @Label("User") String user;
        @Label("Success") boolean success;
    }

    @Name("atoz.Debug")
    @Enabled(false) // off unless a recording's settings turn it on
    static class DebugEvent extends Event {
        @Label("Message") String message;
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("jfr-demo");
        Path file = dir.resolve("demo.jfr");
        try (Recording recording = new Recording()) {
            recording.enable(LoginEvent.class);              // explicit, though enabled by default
            recording.enable("jdk.GarbageCollection");       // a built-in JDK event
            recording.start();

            for (String[] attempt : new String[][] {{"alice", "true"}, {"mallory", "false"}, {"mallory", "false"}}) {
                LoginEvent e = new LoginEvent();
                e.begin();
                e.user = attempt[0];
                e.success = Boolean.parseBoolean(attempt[1]);
                e.commit();                                   // end() is implied by commit()
            }
            DebugEvent d = new DebugEvent();
            d.message = "should not be recorded";
            System.out.println("DebugEvent shouldCommit(): " + d.shouldCommit());
            d.commit();
            System.gc();                                      // produces a jdk.GarbageCollection event

            recording.stop();
            recording.dump(file);
        }

        List<RecordedEvent> events = RecordingFile.readAllEvents(file);
        List<RecordedEvent> logins = events.stream()
                .filter(e -> e.getEventType().getName().equals("atoz.Login"))
                .sorted(Comparator.comparing(RecordedEvent::getStartTime))
                .toList();
        System.out.println("atoz.Login events: " + logins.size());
        for (RecordedEvent e : logins) {
            System.out.println("  user=" + e.getString("user") + ", success=" + e.getBoolean("success")
                    + ", has thread: " + (e.getThread() != null) + ", stack trace: " + (e.getStackTrace() != null));
        }
        System.out.println("category: " + logins.get(0).getEventType().getCategoryNames()
                + ", label: " + logins.get(0).getEventType().getLabel());
        System.out.println("durations non-negative: " + logins.stream().noneMatch(e -> e.getDuration().isNegative()));
        System.out.println("atoz.Debug events: " + events.stream().filter(e -> e.getEventType().getName().equals("atoz.Debug")).count());
        System.out.println("jdk.GarbageCollection recorded: "
                + events.stream().anyMatch(e -> e.getEventType().getName().equals("jdk.GarbageCollection")));
        System.out.println("failed logins by mallory: " + logins.stream()
                .filter(e -> !e.getBoolean("success") && e.getString("user").equals("mallory")).count());

        Files.delete(file);
        Files.delete(dir);
    }
}

/* Output:
DebugEvent shouldCommit(): false
atoz.Login events: 3
  user=alice, success=true, has thread: true, stack trace: false
  user=mallory, success=false, has thread: true, stack trace: false
  user=mallory, success=false, has thread: true, stack trace: false
category: [Java A to Z, Security], label: Login attempt
durations non-negative: true
atoz.Debug events: 0
jdk.GarbageCollection recorded: true
failed logins by mallory: 2
*/
