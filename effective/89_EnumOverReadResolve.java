// Effective Java, Item 89: For instance control, prefer enum types to
// readResolve.
//
// A singleton that implements Serializable stops being a singleton: every
// deserialization creates a new instance. readResolve can substitute the
// real instance, but the deserialized temporary object still exists while
// readResolve runs, and if the class has any non-transient object reference
// field, a crafted stream can capture that temporary object before
// readResolve replaces it. So with readResolve every instance field must be
// transient. An enum gets all this right for free: the stream holds only the
// constant's name, the JVM maps it back to the one existing constant, and no
// stream can create a new constant.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;

public class EnumOverReadResolve {
    static final class NaiveElvis implements Serializable {
        static final NaiveElvis INSTANCE = new NaiveElvis();

        private NaiveElvis() {}
    }

    static boolean sawTemporaryInstance;

    static final class Elvis implements Serializable {
        static final Elvis INSTANCE = new Elvis();
        private transient String[] favoriteSongs = {"Hound Dog", "Heartbreak Hotel"}; // transient!

        private Elvis() {}

        @Serial
        private Object readResolve() {
            sawTemporaryInstance = this != INSTANCE; // a second Elvis exists right now
            return INSTANCE; // the temporary one becomes garbage
        }
    }

    enum EnumElvis {
        INSTANCE;
        private final String[] favoriteSongs = {"Hound Dog", "Heartbreak Hotel"};
    }

    public static void main(String[] args) throws Exception {
        System.out.println("naive singleton, same after round trip: "
                + (roundTrip(NaiveElvis.INSTANCE) == NaiveElvis.INSTANCE));
        System.out.println("readResolve,     same after round trip: "
                + (roundTrip(Elvis.INSTANCE) == Elvis.INSTANCE)
                + " (temporary instance created: " + sawTemporaryInstance + ")");
        System.out.println("enum,            same after round trip: "
                + (roundTrip(EnumElvis.INSTANCE) == EnumElvis.INSTANCE));

        // An enum stream carries only the constant's name; a forged name is rejected
        byte[] bytes = serialize(EnumElvis.INSTANCE);
        String s = new String(bytes, StandardCharsets.ISO_8859_1);
        int at = s.lastIndexOf("INSTANCE");
        bytes[at + 7] = 'X'; // "INSTANCE" -> "INSTANCX"
        try {
            deserialize(bytes);
        } catch (InvalidObjectException e) {
            System.out.println("forged enum constant: InvalidObjectException: " + e.getMessage());
        }
    }

    static Object roundTrip(Object o) throws IOException, ClassNotFoundException {
        return deserialize(serialize(o));
    }

    static byte[] serialize(Object o) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(o);
        }
        return bos.toByteArray();
    }

    static Object deserialize(byte[] b) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(b))) {
            return in.readObject();
        }
    }
}

/* Output:
naive singleton, same after round trip: false
readResolve,     same after round trip: true (temporary instance created: true)
enum,            same after round trip: true
forged enum constant: InvalidObjectException: enum constant INSTANCX does not exist in class EnumOverReadResolve$EnumElvis
*/
