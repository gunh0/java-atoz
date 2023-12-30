// Effective Java, Item 90: Consider serialization proxies instead of
// serialized instances.
//
// The serialization proxy pattern removes the "hidden constructor" problem
// altogether. A private static nested class (the proxy) holds the logical
// state; the outer class's writeReplace hands the proxy to the stream
// instead of itself, and the proxy's readResolve rebuilds the outer object
// through its public constructor or static factory - so every invariant
// check and defensive copy runs, and fields can stay final. To make sure no
// stream can create the outer class directly, its readObject always throws.
// Costs: it does not work for classes extendable by clients, nor for object
// graphs with cycles, and it is a little slower than defensive copying.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class SerializationProxyPattern {
    static final class Period implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private final Date start;
        private final Date end;

        Period(Date start, Date end) {
            this.start = new Date(start.getTime());
            this.end = new Date(end.getTime());
            if (this.start.compareTo(this.end) > 0) {
                throw new IllegalArgumentException(start.getTime() + " after " + end.getTime());
            }
        }

        @Override
        public String toString() {
            return "[" + start.getTime() + " .. " + end.getTime() + "]";
        }

        // The logical state, and nothing else
        private static class SerializationProxy implements Serializable {
            @Serial
            private static final long serialVersionUID = 234098243823485285L; // any number
            private final Date start;
            private final Date end;

            SerializationProxy(Period p) {
                this.start = p.start;
                this.end = p.end;
            }

            @Serial
            private Object readResolve() {
                return new Period(start, end); // public API: checks and copies run
            }
        }

        @Serial
        private Object writeReplace() {
            return new SerializationProxy(this);
        }

        @Serial
        private void readObject(ObjectInputStream stream) throws InvalidObjectException {
            throw new InvalidObjectException("Proxy required");
        }
    }

    // Same shape as Period but without the proxy: used only to build a forged
    // stream that claims to contain a Period directly
    static final class Xeriod implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private final Date start;
        private final Date end;

        Xeriod(Date start, Date end) {
            this.start = start;
            this.end = end;
        }
    }

    public static void main(String[] args) throws Exception {
        Period p = new Period(new Date(1000), new Date(2000));
        byte[] bytes = serialize(p);
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        System.out.println("round trip: " + deserialize(bytes));
        System.out.println("stream contains the proxy class: " + text.contains("Period$SerializationProxy"));

        // An edited proxy stream (end before start) goes through the constructor
        byte[] edited = bytes.clone();
        int a = indexOfLong(edited, 1000), b = indexOfLong(edited, 2000);
        writeLong(edited, a, 2000);
        writeLong(edited, b, 1000);
        try {
            deserialize(edited);
        } catch (IllegalArgumentException e) {
            System.out.println("edited proxy stream: rejected by constructor: " + e.getMessage());
        }

        // A forged stream that names Period itself is refused by readObject
        byte[] forged = serialize(new Xeriod(new Date(2000), new Date(1000)));
        String forgedText = new String(forged, StandardCharsets.ISO_8859_1).replace("$Xeriod", "$Period");
        try {
            deserialize(forgedText.getBytes(StandardCharsets.ISO_8859_1));
        } catch (InvalidObjectException e) {
            System.out.println("forged direct stream: InvalidObjectException: " + e.getMessage());
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

    static int indexOfLong(byte[] b, long v) {
        outer:
        for (int i = 0; i + 8 <= b.length; i++) {
            for (int k = 0; k < 8; k++) {
                if (b[i + k] != (byte) (v >>> (56 - 8 * k))) {
                    continue outer;
                }
            }
            return i;
        }
        throw new IllegalStateException("not found: " + v);
    }

    static void writeLong(byte[] b, int at, long v) {
        for (int k = 0; k < 8; k++) {
            b[at + k] = (byte) (v >>> (56 - 8 * k));
        }
    }
}

/* Output:
round trip: [1000 .. 2000]
stream contains the proxy class: true
edited proxy stream: rejected by constructor: 2000 after 1000
forged direct stream: InvalidObjectException: Proxy required
*/
