// Runtime polymorphism (dynamic method dispatch): a superclass reference can
// point at any subclass object, and calling an overridden method runs the
// version of the ACTUAL object, chosen at runtime. Code written against the
// supertype therefore works unchanged for new subtypes.
//  - upcasting (Sub -> Super) is implicit and always safe
//  - downcasting (Super -> Sub) needs a cast and fails with
//    ClassCastException if the object is not really a Sub; check first with
//    instanceof (Java 16+ binds the variable in the same step)
//  - only methods are polymorphic: fields are resolved by the declared type
import java.util.List;

public class PolymorphismExample {
    public static void main(String[] args) {
        List<Notifier> notifiers = List.of(
                new EmailNotifier("ops@example.com"),
                new SmsNotifier("+82-10-0000-0000"),
                new Notifier());

        // One loop, three behaviours: dispatch picks the method per object
        for (Notifier n : notifiers) {
            n.send("disk usage above 90%");
        }

        // Downcasting safely with an instanceof pattern
        for (Notifier n : notifiers) {
            if (n instanceof SmsNotifier sms) {
                System.out.println("SMS segments needed: " + sms.segments("x".repeat(200)));
            }
        }

        // A blind downcast fails at runtime
        Notifier plain = notifiers.get(0);
        try {
            SmsNotifier wrong = (SmsNotifier) plain;
            System.out.println("unreachable " + wrong);
        } catch (ClassCastException e) {
            System.out.println("ClassCastException: EmailNotifier is not an SmsNotifier");
        }

        // Fields are not polymorphic
        Notifier asSuper = new SmsNotifier("+82-10-1111-1111");
        SmsNotifier asSub = (SmsNotifier) asSuper;
        System.out.println("field via Notifier type:    " + asSuper.channel);
        System.out.println("field via SmsNotifier type: " + asSub.channel);
        System.out.println("method via Notifier type:   " + asSuper.channelName());
    }
}

class Notifier {
    String channel = "console";

    void send(String message) {
        System.out.println("[console] " + message);
    }

    String channelName() {
        return channel;
    }
}

class EmailNotifier extends Notifier {
    private final String address;

    EmailNotifier(String address) {
        this.address = address;
    }

    @Override
    void send(String message) {
        System.out.println("[email to " + address + "] " + message);
    }
}

class SmsNotifier extends Notifier {
    String channel = "sms";            // hides Notifier.channel, does not override it
    private final String number;

    SmsNotifier(String number) {
        this.number = number;
    }

    @Override
    void send(String message) {
        System.out.println("[sms to " + number + "] " + message);
    }

    @Override
    String channelName() {
        return channel;
    }

    int segments(String message) {
        return (message.length() + 159) / 160;
    }
}

/* Output:
[email to ops@example.com] disk usage above 90%
[sms to +82-10-0000-0000] disk usage above 90%
[console] disk usage above 90%
SMS segments needed: 2
ClassCastException: EmailNotifier is not an SmsNotifier
field via Notifier type:    console
field via SmsNotifier type: sms
method via Notifier type:   sms
*/
