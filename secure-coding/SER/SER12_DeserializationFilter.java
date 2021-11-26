// SER12-J. Prevent deserialization of untrusted data
// https://wiki.sei.cmu.edu/confluence/display/java/SER12-J.+Prevent+deserialization+of+untrusted+data
//
// ObjectInputStream.readObject() instantiates whatever classes the byte stream
// names and runs their readObject() methods BEFORE your code gets to cast the
// result. A crafted stream can therefore chain "gadget" classes already on the
// classpath into arbitrary behavior. The cast after readObject() is too late.
// Defenses, best first: do not deserialize untrusted data at all; otherwise
// install an allow-list ObjectInputFilter (JEP 290, Java 9) that rejects every
// class not expected, plus limits on depth, references and size. Java 17 adds
// a JVM-wide filter factory (JEP 415) so a filter is applied to every stream,
// including those created inside libraries you do not control.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class SER12_DeserializationFilter {
    // A real application installs the factory once at startup (or with
    // -Djdk.serialFilterFactory=...); it cannot be replaced after the first
    // stream exists. This demo installs it first but switches enforcement on
    // only for the last part, so both behaviors can be shown in one run.
    static volatile boolean enforceJvmWide = false;

    public static void main(String[] args) throws Exception {
        ObjectInputFilter.Config.setSerialFilterFactory((current, requested) -> {
            if (!enforceJvmWide) {
                return requested;                 // default behavior
            }
            return requested == null ? ALLOW_LIST : ObjectInputFilter.merge(requested, ALLOW_LIST);
        });

        byte[] expected = serialize(new Message("hello"));
        byte[] malicious = serialize(new Gadget("rm -rf /")); // the "attacker's" payload

        // Noncompliant: deserialize first, check the type afterwards
        System.out.println("== noncompliant ==");
        System.out.println(receiveNoncompliant(expected));
        System.out.println(receiveNoncompliant(malicious));

        // Compliant: an allow-list filter on the stream
        System.out.println("== compliant: per-stream filter ==");
        System.out.println(receiveCompliant(expected));
        System.out.println(receiveCompliant(malicious));

        // Compliant, JVM-wide (Java 17): a filter factory decides the filter
        // for every ObjectInputStream, even ones that never call setObjectInputFilter
        System.out.println("== compliant: JVM-wide filter factory ==");
        enforceJvmWide = true;
        System.out.println(receiveNoncompliant(expected)); // same old code, now protected
        System.out.println(receiveNoncompliant(malicious));
    }

    // Allow exactly what the protocol needs; everything else is rejected ("!*").
    // maxdepth/maxrefs/maxbytes also stop resource-exhaustion payloads.
    static final ObjectInputFilter ALLOW_LIST = ObjectInputFilter.Config.createFilter(
            "maxdepth=5;maxrefs=100;maxbytes=10000;Message;java.lang.String;!*");

    static String receiveNoncompliant(byte[] data) {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data))) {
            Object o = in.readObject();            // the gadget has already run here
            if (!(o instanceof Message m)) {
                return "rejected after deserialization: " + o.getClass().getSimpleName();
            }
            return "accepted " + m;
        } catch (InvalidClassException e) {
            return "blocked by filter: " + e.getMessage();
        } catch (IOException | ClassNotFoundException e) {
            return "error: " + e;
        }
    }

    static String receiveCompliant(byte[] data) {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data))) {
            in.setObjectInputFilter(ALLOW_LIST);  // before the first readObject()
            Message m = (Message) in.readObject();
            return "accepted " + m;
        } catch (InvalidClassException e) {
            return "blocked by filter: " + e.getMessage();
        } catch (IOException | ClassNotFoundException e) {
            return "error: " + e;
        }
    }

    static byte[] serialize(Object o) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(o);
        }
        return bytes.toByteArray();
    }
}

// The type the protocol expects
class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String text;

    Message(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return "Message[" + text + "]";
    }
}

// Stand-in for a gadget class that happens to be on the classpath: its
// readObject() has a side effect. Real gadget chains reach Runtime.exec, JNDI
// lookups or file writes; this one only prints.
class Gadget implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String command;

    Gadget(String command) {
        this.command = command;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        System.out.println("  !! gadget side effect would run: " + command);
    }
}

/* Output:
== noncompliant ==
accepted Message[hello]
  !! gadget side effect would run: rm -rf /
rejected after deserialization: Gadget
== compliant: per-stream filter ==
accepted Message[hello]
blocked by filter: filter status: REJECTED
== compliant: JVM-wide filter factory ==
accepted Message[hello]
blocked by filter: filter status: REJECTED
*/
