// Adapter (structural): convert the interface of an existing class into the
// interface clients expect, so classes with incompatible interfaces can work
// together.
//
// Our application reports security alerts through a small AlertSink
// interface. A vendor library we cannot change speaks a different language:
// numeric severities and a three-argument write(). The adapter implements
// AlertSink and translates each call; neither side has to change.
// JDK examples: InputStreamReader adapts a byte InputStream to a char Reader,
// and Arrays.asList adapts an array to the List interface (writes go through).
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AdapterDemo {
    // The target interface our code is written against
    interface AlertSink {
        void raise(Severity severity, String source, String message);
    }

    enum Severity { LOW, MEDIUM, HIGH, CRITICAL }

    // The adaptee: a third-party client with its own conventions
    // (syslog-style numbers where SMALLER means MORE severe)
    static final class VendorSiemClient {
        final List<String> sent = new ArrayList<>();

        void write(int level, String tag, String text) {
            sent.add("level=" + level + " tag=" + tag + " text=\"" + text + "\"");
        }
    }

    // The adapter: implements the target, delegates to the adaptee
    static final class SiemAlertSink implements AlertSink {
        private final VendorSiemClient client;

        SiemAlertSink(VendorSiemClient client) { this.client = client; }

        @Override
        public void raise(Severity severity, String source, String message) {
            client.write(toLevel(severity), source.toUpperCase(), message);
        }

        private static int toLevel(Severity s) {
            return switch (s) {
                case CRITICAL -> 2;
                case HIGH -> 3;
                case MEDIUM -> 4;
                case LOW -> 6;
            };
        }
    }

    // Application code only knows AlertSink
    static void detectBruteForce(AlertSink sink, String user, int failures) {
        if (failures >= 10) {
            sink.raise(Severity.HIGH, "auth", failures + " failed logins for " + user);
        } else if (failures >= 5) {
            sink.raise(Severity.LOW, "auth", failures + " failed logins for " + user);
        }
    }

    public static void main(String[] args) throws IOException {
        VendorSiemClient vendor = new VendorSiemClient();
        AlertSink sink = new SiemAlertSink(vendor);

        detectBruteForce(sink, "alice", 6);
        detectBruteForce(sink, "root", 42);
        sink.raise(Severity.CRITICAL, "edr", "credential dumping tool detected");
        vendor.sent.forEach(System.out::println);

        // JDK adapter 1: bytes -> characters
        byte[] utf8 = "line one\nline two\n".getBytes(StandardCharsets.UTF_8);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(utf8), StandardCharsets.UTF_8))) {
            System.out.println("reader lines: " + reader.lines().toList());
        }

        // JDK adapter 2: an array seen through the List interface
        String[] ports = {"22", "80", "443"};
        List<String> view = Arrays.asList(ports);
        view.set(0, "2222"); // writes through to the array
        System.out.println("array after list.set: " + Arrays.toString(ports));
        try {
            view.add("8080"); // an array cannot grow, so the adapter refuses
        } catch (UnsupportedOperationException e) {
            System.out.println("list.add on Arrays.asList: UnsupportedOperationException");
        }
    }
}

/* Output:
level=6 tag=AUTH text="6 failed logins for alice"
level=3 tag=AUTH text="42 failed logins for root"
level=2 tag=EDR text="credential dumping tool detected"
reader lines: [line one, line two]
array after list.set: [2222, 80, 443]
list.add on Arrays.asList: UnsupportedOperationException
*/
