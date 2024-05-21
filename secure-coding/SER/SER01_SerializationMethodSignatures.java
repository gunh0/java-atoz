// SER01-J. Do not deviate from the proper signatures of serialization methods
// https://wiki.sei.cmu.edu/confluence/display/java/SER01-J.+Do+not+deviate+from+the+proper+signatures+of+serialization+methods
//
// Serialization finds writeObject, readObject and readObjectNoData by
// reflection, and only if they have exactly the expected signature:
//     private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
// Make it public, static, or give it another return type and the runtime
// silently ignores it. The validation inside never runs, and deserialized
// objects skip every check. (readResolve and writeReplace take any access
// modifier but must return Object.) Declare the methods exactly as
// specified, mark them with @Serial (Java 14+) so javac -Xlint:serial
// can flag mistakes, and test that invalid streams are rejected.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;

public class SER01_SerializationMethodSignatures {
    public static void main(String[] args) throws Exception {
        // Simulate a crafted stream: an attacker can write any field values,
        // which we mimic by bypassing the constructor check.
        byte[] badPercentNoncompliant = serialize(PercentNoncompliant.forge(250));
        byte[] badPercentCompliant = serialize(PercentCompliant.forge(250));

        PercentNoncompliant p1 = (PercentNoncompliant) deserialize(badPercentNoncompliant);
        System.out.println("noncompliant (public readObject): accepted value " + p1.value);

        try {
            deserialize(badPercentCompliant);
        } catch (InvalidObjectException e) {
            System.out.println("compliant (private readObject):   rejected: " + e.getMessage());
        }

        PercentCompliant ok = (PercentCompliant) deserialize(serialize(new PercentCompliant(42)));
        System.out.println("compliant valid stream:           value " + ok.value);
    }

    static byte[] serialize(Object o) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(o);
        }
        return bytes.toByteArray();
    }

    static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return in.readObject();
        }
    }
}

class PercentNoncompliant implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    int value;

    PercentNoncompliant(int value) {
        if (value < 0 || value > 100) throw new IllegalArgumentException("0..100");
        this.value = value;
    }

    static PercentNoncompliant forge(int value) {
        PercentNoncompliant p = new PercentNoncompliant(0);
        p.value = value;
        return p;
    }

    // Noncompliant: public, so serialization never calls it
    public void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (value < 0 || value > 100) throw new InvalidObjectException("value out of range");
    }
}

class PercentCompliant implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    int value;

    PercentCompliant(int value) {
        if (value < 0 || value > 100) throw new IllegalArgumentException("0..100");
        this.value = value;
    }

    static PercentCompliant forge(int value) {
        PercentCompliant p = new PercentCompliant(0);
        p.value = value;
        return p;
    }

    // Compliant: exactly the signature serialization looks for
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (value < 0 || value > 100) throw new InvalidObjectException("value out of range");
    }
}

/* Output:
noncompliant (public readObject): accepted value 250
compliant (private readObject):   rejected: value out of range
compliant valid stream:           value 42
*/
