// Bridge (structural): decouple an abstraction from its implementation so the
// two can vary independently.
//
// Without a bridge, N kinds of notification times M delivery channels means
// N x M subclasses (SecurityAlertEmail, SecurityAlertSms, ReportEmail, ...).
// With a bridge the abstraction (what is being said) holds a reference to an
// implementor (how it is delivered), and both hierarchies grow separately:
// adding a channel is one class, adding a notification kind is one class.
// JDK example: JDBC. java.sql.DriverManager / Connection is the abstraction
// your code uses; each vendor's Driver is the implementation behind it.
import java.util.ArrayList;
import java.util.List;

public class BridgeDemo {
    // Implementor hierarchy: delivery channels
    interface Channel {
        String name();
        int maxLength();
        void deliver(String recipient, String text);
    }

    static final List<String> OUTBOX = new ArrayList<>();

    static final class EmailChannel implements Channel {
        public String name() { return "email"; }
        public int maxLength() { return 1000; }
        public void deliver(String recipient, String text) { OUTBOX.add("[email to " + recipient + "] " + text); }
    }

    static final class SmsChannel implements Channel {
        public String name() { return "sms"; }
        public int maxLength() { return 40; }
        public void deliver(String recipient, String text) { OUTBOX.add("[sms to " + recipient + "] " + text); }
    }

    static final class ChatChannel implements Channel {
        public String name() { return "chat"; }
        public int maxLength() { return 200; }
        public void deliver(String recipient, String text) { OUTBOX.add("[chat #" + recipient + "] " + text); }
    }

    // Abstraction hierarchy: kinds of notification. The bridge is the
    // `channel` field; the abstraction uses only the Channel interface.
    abstract static class Notification {
        protected final Channel channel;

        Notification(Channel channel) { this.channel = channel; }

        abstract String compose();

        final void send(String recipient) {
            String text = compose();
            if (text.length() > channel.maxLength()) {
                text = text.substring(0, channel.maxLength() - 3) + "...";
            }
            channel.deliver(recipient, text);
        }
    }

    static final class SecurityAlert extends Notification {
        private final String event;
        SecurityAlert(Channel channel, String event) { super(channel); this.event = event; }
        String compose() { return "SECURITY ALERT: " + event; }
    }

    static final class WeeklyReport extends Notification {
        private final int blocked;
        private final int allowed;
        WeeklyReport(Channel channel, int blocked, int allowed) {
            super(channel);
            this.blocked = blocked;
            this.allowed = allowed;
        }
        String compose() {
            return "Weekly firewall report: " + blocked + " blocked, " + allowed + " allowed connections";
        }
    }

    public static void main(String[] args) {
        Channel email = new EmailChannel();
        Channel sms = new SmsChannel();
        Channel chat = new ChatChannel();

        // Any notification kind works with any channel: 2 + 3 classes, 6 combinations
        new SecurityAlert(sms, "new admin login from unknown country").send("+82-10-0000-0000");
        new SecurityAlert(chat, "new admin login from unknown country").send("soc");
        new WeeklyReport(email, 1532, 98211).send("ciso@example.com");
        new WeeklyReport(sms, 1532, 98211).send("+82-10-0000-0000");

        OUTBOX.forEach(System.out::println);
    }
}

/* Output:
[sms to +82-10-0000-0000] SECURITY ALERT: new admin login from ...
[chat #soc] SECURITY ALERT: new admin login from unknown country
[email to ciso@example.com] Weekly firewall report: 1532 blocked, 98211 allowed connections
[sms to +82-10-0000-0000] Weekly firewall report: 1532 blocked,...
*/
