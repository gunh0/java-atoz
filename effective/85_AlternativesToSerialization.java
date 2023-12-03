// Effective Java, Item 85: Prefer alternatives to Java serialization.
//
// ObjectInputStream.readObject is a constructor for almost any Serializable
// class on the classpath, driven entirely by the bytes it reads. Untrusted
// bytes can therefore make it instantiate unexpected classes or burn
// unbounded CPU and memory. A "deserialization bomb" needs no exotic class
// at all: under 6 KB of nested HashSets forces about 2^100 hashCode
// calls while the sets are rebuilt. The best defense is to never
// deserialize untrusted data - use a simple, explicit data format instead
// (JSON, protobuf, or plain DataOutput as below). If you cannot avoid it,
// install an ObjectInputFilter (Java 9) that allowlists classes and limits
// depth, references and array sizes. This demo never runs the bomb
// unfiltered.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class AlternativesToSerialization {
    record Point(int x, int y) implements Serializable {}

    // Benign class standing in for "anything else on the classpath"
    static class Unexpected implements Serializable {}

    static byte[] serialize(Object o) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(o);
        }
        return bos.toByteArray();
    }

    static Object deserialize(byte[] bytes, ObjectInputFilter filter) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            in.setObjectInputFilter(filter);
            return in.readObject();
        }
    }

    // 100 levels of sets, each holding the next two; cheap to build and write
    static byte[] bomb() throws IOException {
        Set<Object> root = new HashSet<>();
        Set<Object> s1 = root;
        Set<Object> s2 = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            Set<Object> t1 = new HashSet<>();
            Set<Object> t2 = new HashSet<>();
            t1.add("foo"); // make t1 unequal to t2
            s1.add(t1);
            s1.add(t2);
            s2.add(t1);
            s2.add(t2);
            s1 = t1;
            s2 = t2;
        }
        return serialize(root);
    }

    public static void main(String[] args) throws Exception {
        // Allowlist only what we expect, and cap the graph depth
        ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
                "maxdepth=10;AlternativesToSerialization$Point;java.util.HashSet;java.lang.String;!*");

        Object ok = deserialize(serialize(new Point(3, 4)), filter);
        System.out.println("expected class: " + ok);

        try {
            deserialize(serialize(new Unexpected()), filter);
        } catch (InvalidClassException e) {
            System.out.println("unexpected class: " + e.getMessage());
        }

        byte[] bomb = bomb();
        System.out.println("bomb size: " + bomb.length + " bytes");
        try {
            deserialize(bomb, filter);
        } catch (InvalidClassException e) {
            System.out.println("bomb with maxdepth=10: " + e.getMessage());
        }

        // Alternative: an explicit format that can only ever produce a Point
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bos)) {
            out.writeInt(3);
            out.writeInt(4);
        }
        byte[] wire = bos.toByteArray();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(wire))) {
            System.out.println("explicit format: " + new Point(in.readInt(), in.readInt()) + " in "
                    + wire.length + " bytes (Java serialization: " + serialize(new Point(3, 4)).length + ")");
        }
    }
}

/* Output:
expected class: Point[x=3, y=4]
unexpected class: filter status: REJECTED
bomb size: 5744 bytes
bomb with maxdepth=10: filter status: REJECTED
explicit format: Point[x=3, y=4] in 8 bytes (Java serialization: 70)
*/
