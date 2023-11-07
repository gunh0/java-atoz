// SER03-J. Do not serialize unencrypted sensitive data
// https://wiki.sei.cmu.edu/confluence/display/java/SER03-J.+Do+not+serialize+unencrypted+sensitive+data
//
// Java serialization writes every non-transient field, private ones
// included, into a byte stream that anyone holding the bytes can read:
// a session cache on disk, a message queue, an HTTP cookie. Access
// modifiers protect nothing once an object is serialized. Keep secrets out
// of the serialized form: mark them transient (and re-obtain them after
// deserialization), declare an explicit serialPersistentFields list, or
// write a custom form that encrypts them. Using a record does not change
// this: its components are serialized too.
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;

public class SER03_SensitiveSerializedData {
    public static void main(String[] args) throws IOException {
        byte[] bad = serialize(new SessionNoncompliant("alice", "s3cr3t-api-key"));
        byte[] good = serialize(new SessionCompliant("alice", "s3cr3t-api-key"));
        System.out.println("noncompliant stream contains the key: " + contains(bad, "s3cr3t-api-key"));
        System.out.println("compliant stream contains the key:    " + contains(good, "s3cr3t-api-key"));
        System.out.println("compliant stream still has the user:  " + contains(good, "alice"));
    }

    static byte[] serialize(Object o) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(o);
        }
        return bytes.toByteArray();
    }

    // What anyone who gets hold of the bytes can do: look for strings in them
    static boolean contains(byte[] data, String s) {
        return new String(data, StandardCharsets.ISO_8859_1).contains(s);
    }
}

// Noncompliant: the private API key goes into the stream
class SessionNoncompliant implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String user;
    private final String apiKey;

    SessionNoncompliant(String user, String apiKey) {
        this.user = user;
        this.apiKey = apiKey;
    }
}

// Compliant: the key is transient and is looked up again when needed
class SessionCompliant implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String user;
    private transient String apiKey;

    SessionCompliant(String user, String apiKey) {
        this.user = user;
        this.apiKey = apiKey;
    }

    String apiKey() {
        if (apiKey == null) {
            apiKey = lookUpKeyFromVault(user); // after deserialization
        }
        return apiKey;
    }

    private static String lookUpKeyFromVault(String user) {
        return "(key for " + user + " from the secret store)";
    }
}

/* Output:
noncompliant stream contains the key: true
compliant stream contains the key:    false
compliant stream still has the user:  true
*/
