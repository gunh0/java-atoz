// SER00-J. Enable serialization compatibility during class evolution
// https://wiki.sei.cmu.edu/confluence/display/java/SER00-J.+Enable+serialization+compatibility+during+class+evolution
//
// Without an explicit serialVersionUID, Java computes one from the class's
// structure (name, fields, methods, interfaces). Any change, even adding a
// harmless field or a method, changes the computed value, and objects
// serialized by the old version can no longer be read: deserialization
// fails with InvalidClassException. Stored sessions, queued messages and
// caches break on upgrade, and a compiler-dependent value makes peers
// built with different compilers disagree. Declare
// private static final long serialVersionUID and change it only on purpose.
//
// The demo stands in for "the same class in version 1 and version 2" by
// using two classes with same-length names and swapping the name in the
// serialized bytes.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;

public class SER00_SerialVersionUID {
    public static void main(String[] args) throws Exception {
        // Noncompliant: no serialVersionUID; version 2 adds a field
        byte[] saved = serialize(new SessionNoUidV1("alice"));
        try {
            Object restored = deserialize(rename(saved, "SessionNoUidV1", "SessionNoUidV2"));
            System.out.println("noncompliant: restored " + restored);
        } catch (InvalidClassException e) {
            System.out.println("noncompliant: InvalidClassException, local class incompatible? "
                    + e.getMessage().contains("local class incompatible"));
        }

        // Compliant: both versions declare the same serialVersionUID
        byte[] savedOk = serialize(new SessionUidV1("alice"));
        Object restored = deserialize(rename(savedOk, "SessionUidV1", "SessionUidV2"));
        System.out.println("compliant:    restored " + restored);
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

    // Replaces a class name by another of the same length in a serialized stream
    static byte[] rename(byte[] data, String from, String to) {
        byte[] f = from.getBytes(StandardCharsets.US_ASCII);
        byte[] t = to.getBytes(StandardCharsets.US_ASCII);
        byte[] result = data.clone();
        outer:
        for (int i = 0; i <= result.length - f.length; i++) {
            for (int j = 0; j < f.length; j++) {
                if (result[i + j] != f[j]) {
                    continue outer;
                }
            }
            System.arraycopy(t, 0, result, i, t.length);
        }
        return result;
    }
}

// Version 1 and version 2 of a session class, without serialVersionUID
class SessionNoUidV1 implements Serializable {
    String user;

    SessionNoUidV1(String user) {
        this.user = user;
    }
}

class SessionNoUidV2 implements Serializable {
    String user;
    String locale; // added in version 2

    @Override
    public String toString() {
        return "user=" + user + ", locale=" + locale;
    }
}

// The same evolution with an explicit serialVersionUID
class SessionUidV1 implements Serializable {
    private static final long serialVersionUID = 1L;
    String user;

    SessionUidV1(String user) {
        this.user = user;
    }
}

class SessionUidV2 implements Serializable {
    private static final long serialVersionUID = 1L;
    String user;
    String locale; // added in version 2; old streams leave it null

    @Override
    public String toString() {
        return "user=" + user + ", locale=" + locale;
    }
}

/* Output:
noncompliant: InvalidClassException, local class incompatible? true
compliant:    restored user=alice, locale=null
*/
