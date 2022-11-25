// SER05-J. Do not serialize instances of inner classes
// https://wiki.sei.cmu.edu/confluence/display/java/SER05-J.+Do+not+serialize+instances+of+inner+classes
//
// An inner (non-static nested, local or anonymous) class holds a hidden
// reference to its enclosing instance, and the serialized form of an inner
// class object therefore includes the WHOLE enclosing object: every field,
// including secrets the author never meant to persist or send. If the
// enclosing class is not serializable, serialization fails instead. The
// hidden field's name and the synthetic members are compiler-specific too,
// so the serialized form is not portable between compilers. Make nested
// classes that need to be serialized static, and pass in only the data they
// need.
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;

public class SER05_InnerClassSerialization {
    public static void main(String[] args) throws IOException {
        UserSession session = new UserSession("alice", "tok_9f8e7d6c5b4a");

        // Noncompliant: serializing the inner object drags the session along
        byte[] inner = serialize(session.innerPreferences("dark"));
        System.out.println("noncompliant: " + inner.length + " bytes, contains auth token: " + contains(inner, "tok_9f8e7d6c5b4a"));

        // Noncompliant, enclosing class not serializable: it does not even work
        try {
            serialize(new Widget().innerState());
        } catch (NotSerializableException e) {
            System.out.println("noncompliant: NotSerializableException: " + e.getMessage());
        }

        // Compliant: static nested class with exactly the data it needs
        byte[] nested = serialize(session.preferences("dark"));
        System.out.println("compliant:    " + nested.length + " bytes, contains auth token: " + contains(nested, "tok_9f8e7d6c5b4a"));
    }

    static byte[] serialize(Object o) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(o);
        }
        return bos.toByteArray();
    }

    static boolean contains(byte[] data, String text) {
        return new String(data, StandardCharsets.ISO_8859_1).contains(text);
    }
}

class UserSession implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String user;
    private final String authToken; // must never be persisted

    UserSession(String user, String authToken) {
        this.user = user;
        this.authToken = authToken;
    }

    // Noncompliant: inner class
    class InnerPreferences implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String theme;

        InnerPreferences(String theme) {
            this.theme = theme;
        }

        String owner() {
            return user; // uses the enclosing instance
        }
    }

    // Compliant: static nested class, explicit data
    static final class Preferences implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String owner;
        private final String theme;

        Preferences(String owner, String theme) {
            this.owner = owner;
            this.theme = theme;
        }
    }

    InnerPreferences innerPreferences(String theme) {
        return new InnerPreferences(theme);
    }

    Preferences preferences(String theme) {
        return new Preferences(user, theme);
    }
}

class Widget {
    private final String label = "ok";

    class State implements Serializable {
        private static final long serialVersionUID = 1L;

        String label() {
            return label;
        }
    }

    State innerState() {
        return new State();
    }
}

/* Output:
noncompliant: 194 bytes, contains auth token: true
noncompliant: NotSerializableException: Widget
compliant:    101 bytes, contains auth token: false
*/
