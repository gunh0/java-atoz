// OWASP Top 10 2021: A08:2021 Software and Data Integrity Failures, insecure deserialization (CWE-502)
// https://owasp.org/Top10/A08_2021-Software_and_Data_Integrity_Failures/
//
// Java deserialization normally rebuilds an object WITHOUT running its
// constructor: field values are copied from the stream, so a crafted stream
// can produce objects that violate every invariant the constructor checks
// (and, with gadget classes on the class path, run code). Records are
// different by design: a record is deserialized by calling its canonical
// constructor with the values from the stream, so validation in a compact
// constructor also guards deserialization. Records are not a complete
// defence, though: the stream may name other classes. Combine them with an
// ObjectInputFilter that allows only the expected classes, and keep
// untrusted data on simpler formats when you can.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.InvalidObjectException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;

public class A08_IntegrityFailures_RecordDeserialization {
    // A discount window: start must not be after end
    static class RangeClass implements Serializable {
        private static final long serialVersionUID = 1L;
        final int start, end;

        RangeClass(int start, int end) {
            if (start > end) throw new IllegalArgumentException("start > end");
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return "RangeClass[start=" + start + ", end=" + end + "]";
        }
    }

    record RangeRecord(int start, int end) implements Serializable {
        RangeRecord {
            if (start > end) throw new IllegalArgumentException("start > end");
        }
    }

    static final int START = 0x11223344, END = 0x55667788;

    public static void main(String[] args) throws Exception {
        // Craft streams: serialize valid objects, then overwrite `start` with a huge value
        byte[] classStream = tamper(serialize(new RangeClass(START, END)));
        byte[] recordStream = tamper(serialize(new RangeRecord(START, END)));

        Object c = deserialize(classStream, null);
        System.out.println("noncompliant (class):  accepted " + c);

        try {
            deserialize(recordStream, null);
        } catch (InvalidObjectException e) {
            System.out.println("compliant (record):    " + e.getClass().getSimpleName()
                    + ", caused by " + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage());
        }

        // Records do not stop a stream from naming other classes: add a filter
        ObjectInputFilter onlyRange = ObjectInputFilter.Config.createFilter(
                RangeRecord.class.getName() + ";!*");
        System.out.println("filtered, valid record: " + deserialize(serialize(new RangeRecord(1, 2)), onlyRange));
        try {
            deserialize(serialize(new ArrayList<>()), onlyRange);
        } catch (InvalidClassException e) {
            System.out.println("filtered, ArrayList:    " + e.getClass().getSimpleName() + " (class not allowed)");
        }
    }

    static byte[] serialize(Object o) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(o);
        }
        return bytes.toByteArray();
    }

    static Object deserialize(byte[] data, ObjectInputFilter filter) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data))) {
            if (filter != null) in.setObjectInputFilter(filter);
            return in.readObject();
        }
    }

    // Replace the 4 bytes of START (big-endian in the stream) with 0x7fffffff
    static byte[] tamper(byte[] stream) {
        byte[] needle = {0x11, 0x22, 0x33, 0x44};
        for (int i = 0; i + 4 <= stream.length; i++) {
            if (stream[i] == needle[0] && stream[i + 1] == needle[1] && stream[i + 2] == needle[2] && stream[i + 3] == needle[3]) {
                stream[i] = 0x7f;
                stream[i + 1] = stream[i + 2] = stream[i + 3] = (byte) 0xff;
                return stream;
            }
        }
        throw new IllegalStateException("field not found");
    }
}

/* Output:
noncompliant (class):  accepted RangeClass[start=2147483647, end=1432778632]
compliant (record):    InvalidObjectException, caused by IllegalArgumentException: start > end
filtered, valid record: RangeRecord[start=1, end=2]
filtered, ArrayList:    InvalidClassException (class not allowed)
*/
