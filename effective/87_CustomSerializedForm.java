// Effective Java, Item 87: Consider using a custom serialized form.
//
// The default serialized form describes the object's physical
// representation - every field, every node, every link. That is fine when
// the physical representation *is* the logical content (a point's x and y),
// but for something like a linked list it is a disaster: the form is tied to
// the implementation forever, it is bloated (each node and both links are
// recorded), and writing it recurses once per node, so long lists overflow
// the stack. A custom form records only the logical data (the size and the
// strings in order) via writeObject/readObject, marks implementation fields
// transient, and still declares an explicit serialVersionUID.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CustomSerializedForm {
    // Default form: the node structure itself is serialized
    static final class DefaultStringList implements Serializable {
        private static final long serialVersionUID = 1L;
        private int size;
        private Entry head, tail;

        private static class Entry implements Serializable {
            String data;
            Entry next, previous;
        }

        void add(String s) {
            Entry e = new Entry();
            e.data = s;
            e.previous = tail;
            if (tail == null) {
                head = e;
            } else {
                tail.next = e;
            }
            tail = e;
            size++;
        }
    }

    // Custom form: just the size followed by the strings
    static final class StringList implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private transient int size;
        private transient Entry head, tail;

        private static class Entry { // no longer Serializable
            String data;
            Entry next;
        }

        void add(String s) {
            Entry e = new Entry();
            e.data = s;
            if (tail == null) {
                head = e;
            } else {
                tail.next = e;
            }
            tail = e;
            size++;
        }

        List<String> toList() {
            List<String> l = new ArrayList<>();
            for (Entry e = head; e != null; e = e.next) {
                l.add(e.data);
            }
            return l;
        }

        @Serial
        private void writeObject(ObjectOutputStream s) throws IOException {
            s.defaultWriteObject(); // no non-transient fields, but keeps the form extensible
            s.writeInt(size);
            for (Entry e = head; e != null; e = e.next) {
                s.writeObject(e.data);
            }
        }

        @Serial
        private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
            s.defaultReadObject();
            int count = s.readInt();
            for (int i = 0; i < count; i++) {
                add((String) s.readObject());
            }
        }
    }

    static byte[] serialize(Object o) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(o);
        }
        return bos.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        DefaultStringList d = new DefaultStringList();
        StringList c = new StringList();
        for (int i = 0; i < 100; i++) {
            d.add("s" + i);
            c.add("s" + i);
        }
        System.out.println("100 strings, default form: " + serialize(d).length + " bytes");
        System.out.println("100 strings, custom form:  " + serialize(c).length + " bytes");

        byte[] bytes = serialize(c);
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            List<String> back = ((StringList) in.readObject()).toList();
            System.out.println("custom round trip: " + back.size() + " strings, first " + back.get(0)
                    + ", last " + back.get(back.size() - 1));
        }

        // Long lists: the default form recurses once per node
        DefaultStringList longDefault = new DefaultStringList();
        StringList longCustom = new StringList();
        for (int i = 0; i < 100_000; i++) {
            longDefault.add("x");
            longCustom.add("x");
        }
        try {
            serialize(longDefault);
            System.out.println("default form, 100000 nodes: ok");
        } catch (StackOverflowError e) {
            System.out.println("default form, 100000 nodes: StackOverflowError");
        }
        serialize(longCustom);
        System.out.println("custom form,  100000 nodes: ok");
    }
}

/* Output:
100 strings, default form: 1941 bytes
100 strings, custom form:  649 bytes
custom round trip: 100 strings, first s0, last s99
default form, 100000 nodes: StackOverflowError
custom form,  100000 nodes: ok
*/
