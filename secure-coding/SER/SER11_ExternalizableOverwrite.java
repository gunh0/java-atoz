// SER11-J. Prevent overwriting of externalizable objects
// https://wiki.sei.cmu.edu/confluence/display/java/SER11-J.+Prevent+overwriting+of+externalizable+objects
//
// An Externalizable class restores its state in readExternal, which must
// be public, and it needs a public no-arg constructor. That means any code
// holding a reference to a live, fully initialized object can call
// readExternal on it again with a stream of its choosing and overwrite its
// fields, even final-looking ones such as an owner or a role. Make
// readExternal refuse to run more than once per object (a guarded
// "initialized" flag, checked and set atomically), or prefer ordinary
// Serializable with private readObject, which outsiders cannot call.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

public class SER11_ExternalizableOverwrite {
    public static void main(String[] args) throws Exception {
        // A crafted stream saying "role = admin"
        byte[] evil = streamOf("mallory", "admin");

        AccountNoncompliant a = new AccountNoncompliant("alice", "user");
        a.readExternal(new ObjectInputStream(new ByteArrayInputStream(evil)));
        System.out.println("noncompliant: live object is now " + a);

        AccountCompliant b = new AccountCompliant("alice", "user");
        try {
            b.readExternal(new ObjectInputStream(new ByteArrayInputStream(evil)));
        } catch (IllegalStateException e) {
            System.out.println("compliant:    second readExternal refused: " + e.getMessage());
        }
        System.out.println("compliant:    live object is still " + b);

        // Normal deserialization still works: it goes through the no-arg constructor once
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(b);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            System.out.println("compliant:    deserialized copy " + in.readObject());
        }
    }

    static byte[] streamOf(String owner, String role) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeUTF(owner);
            out.writeUTF(role);
        }
        return bytes.toByteArray();
    }
}

class AccountNoncompliant implements Externalizable {
    private String owner;
    private String role;

    public AccountNoncompliant() {
    }

    AccountNoncompliant(String owner, String role) {
        this.owner = owner;
        this.role = role;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(owner);
        out.writeUTF(role);
    }

    // Noncompliant: callable on any instance, any number of times
    @Override
    public void readExternal(ObjectInput in) throws IOException {
        owner = in.readUTF();
        role = in.readUTF();
    }

    @Override
    public String toString() {
        return owner + "/" + role;
    }
}

class AccountCompliant implements Externalizable {
    private String owner;
    private String role;
    private boolean initialized;

    public AccountCompliant() {
    }

    AccountCompliant(String owner, String role) {
        this.owner = owner;
        this.role = role;
        this.initialized = true;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(owner);
        out.writeUTF(role);
    }

    // Compliant: only an uninitialized instance (fresh from the no-arg constructor) may be filled
    @Override
    public synchronized void readExternal(ObjectInput in) throws IOException {
        if (initialized) {
            throw new IllegalStateException("object already initialized");
        }
        owner = in.readUTF();
        role = in.readUTF();
        initialized = true;
    }

    @Override
    public String toString() {
        return owner + "/" + role;
    }
}

/* Output:
noncompliant: live object is now mallory/admin
compliant:    second readExternal refused: object already initialized
compliant:    live object is still alice/user
compliant:    deserialized copy alice/user
*/
