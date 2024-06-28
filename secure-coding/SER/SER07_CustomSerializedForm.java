// SER07-J. Do not use the default serialized form for classes with implementation-defined invariants
// https://wiki.sei.cmu.edu/confluence/display/java/SER07-J.+Do+not+use+the+default+serialized+form+for+classes+with+implementation-defined+invariants
//
// The default serialized form writes every non-transient field exactly as
// it is in memory. That is wrong when a field only makes sense inside the
// current JVM: an index into a static table, a handle, a cached hash, an
// interned id assigned at startup. Deserialized in another JVM (or after a
// restart) the raw value still "works" but points at something else. Give
// such classes a custom serialized form: write the logical content (a
// name, not a slot number) in writeObject or a serialization proxy, and
// rebuild the implementation-specific fields when reading, validating them.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SER07_CustomSerializedForm {
    public static void main(String[] args) throws Exception {
        // JVM 1: roles are registered in this order at startup
        RoleTable.reset(List.of("viewer", "editor", "admin"));
        GrantNoncompliant g1 = new GrantNoncompliant("alice", "viewer");
        GrantCompliant g2 = new GrantCompliant("alice", "viewer");
        System.out.println("JVM 1: alice is " + g1.role() + " / " + g2.role());
        byte[] s1 = serialize(g1), s2 = serialize(g2);

        // JVM 2: same roles, different startup order (a new plugin registered first)
        RoleTable.reset(List.of("admin", "viewer", "editor"));
        GrantNoncompliant r1 = (GrantNoncompliant) deserialize(s1);
        GrantCompliant r2 = (GrantCompliant) deserialize(s2);
        System.out.println("JVM 2: noncompliant: alice is " + r1.role());
        System.out.println("JVM 2: compliant:    alice is " + r2.role());

        // A role that does not exist on the receiving side is rejected, not mapped to a slot
        RoleTable.reset(List.of("admin", "editor"));
        try {
            deserialize(s2);
        } catch (InvalidObjectException e) {
            System.out.println("JVM 3: compliant:    rejected: " + e.getMessage());
        }
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

// A JVM-wide registry: the slot numbers depend on registration order
class RoleTable {
    private static final List<String> ROLES = new ArrayList<>();

    static void reset(List<String> roles) {
        ROLES.clear();
        ROLES.addAll(roles);
    }

    static int slotOf(String role) {
        return ROLES.indexOf(role);
    }

    static String nameOf(int slot) {
        return ROLES.get(slot);
    }
}

// Noncompliant: the default form writes the slot number
class GrantNoncompliant implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final String user;
    private final int roleSlot; // only meaningful in the JVM that assigned it

    GrantNoncompliant(String user, String role) {
        this.user = user;
        this.roleSlot = RoleTable.slotOf(role);
    }

    String role() {
        return RoleTable.nameOf(roleSlot);
    }
}

// Compliant: the serialized form is the role NAME; the slot is rebuilt on read
class GrantCompliant implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final String user;
    private transient int roleSlot;

    GrantCompliant(String user, String role) {
        this.user = user;
        this.roleSlot = RoleTable.slotOf(role);
    }

    String role() {
        return RoleTable.nameOf(roleSlot);
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();      // writes `user`
        out.writeUTF(role());          // the logical value
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        String role = in.readUTF();
        int slot = RoleTable.slotOf(role);
        if (slot < 0) {
            throw new InvalidObjectException("unknown role " + role);
        }
        roleSlot = slot;
    }
}

/* Output:
JVM 1: alice is viewer / viewer
JVM 2: noncompliant: alice is admin
JVM 2: compliant:    alice is viewer
JVM 3: compliant:    rejected: unknown role viewer
*/
