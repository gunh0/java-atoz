// Effective Java, Item 86: Implement Serializable with great caution.
//
// Adding "implements Serializable" is one keyword but a long-term cost:
//   - the serialized form becomes part of the exported API: private field
//     names and values are written to the stream, so the internal
//     representation can never change freely (and is readable by anyone);
//   - deserialization is a hidden constructor: objects are created without
//     running any constructor, so invariants checked there are skipped;
//   - every new release must be tested for compatibility with old streams,
//     and the default serialVersionUID changes with almost any edit;
//   - inner classes must not be serializable: their form includes a hidden
//     reference to the enclosing instance.
// Classes designed for inheritance and inner classes should rarely
// implement it; value classes might, after careful thought.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;

public class ImplementSerializableWithCaution {
    static int constructorCalls;

    static final class Temperature implements Serializable {
        private static final long serialVersionUID = 1L;
        private final int kelvin;

        Temperature(int kelvin) {
            constructorCalls++;
            if (kelvin < 0) {
                throw new IllegalArgumentException("below absolute zero: " + kelvin);
            }
            this.kelvin = kelvin;
        }

        @Override
        public String toString() {
            return kelvin + " K";
        }
    }

    // Not serializable itself, but has a serializable inner class
    class Sensor {
        final class Reading implements Serializable {
            final int value = 42;
        }
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

    public static void main(String[] args) throws Exception {
        byte[] bytes = serialize(new Temperature(300));

        // 1. The private field name is now part of a public wire format
        String visible = new String(bytes, StandardCharsets.ISO_8859_1);
        System.out.println("stream mentions private field 'kelvin': " + visible.contains("kelvin"));

        // 2. Deserialization skips the constructor entirely
        constructorCalls = 0;
        System.out.println("round trip: " + deserialize(bytes) + ", constructor calls = " + constructorCalls);

        // ...including its validation: the int field is the last 4 bytes of this stream
        byte[] edited = bytes.clone();
        int n = edited.length;
        int forged = -5;
        edited[n - 4] = (byte) (forged >>> 24);
        edited[n - 3] = (byte) (forged >>> 16);
        edited[n - 2] = (byte) (forged >>> 8);
        edited[n - 1] = (byte) forged;
        System.out.println("edited stream: " + deserialize(edited) + " (the constructor would have refused)");

        // 3. A serializable inner class drags in its enclosing instance
        Sensor.Reading reading = new ImplementSerializableWithCaution().new Sensor().new Reading();
        try {
            serialize(reading);
        } catch (NotSerializableException e) {
            System.out.println("inner class: NotSerializableException for " + e.getMessage());
        }
    }
}

/* Output:
stream mentions private field 'kelvin': true
round trip: 300 K, constructor calls = 0
edited stream: -5 K (the constructor would have refused)
inner class: NotSerializableException for ImplementSerializableWithCaution$Sensor
*/
