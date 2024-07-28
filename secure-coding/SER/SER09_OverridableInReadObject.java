// SER09-J. Do not invoke overridable methods from the readObject() method
// https://wiki.sei.cmu.edu/confluence/display/java/SER09-J.+Do+not+invoke+overridable+methods+from+the+readObject()+method
//
// Deserialization restores a class hierarchy from the top down: the
// superclass's readObject runs before any subclass field has been read. If
// that readObject calls an overridable method, the subclass's override
// runs on a half-built object and sees null or zero fields. It may crash,
// make a decision based on missing data, or (in a hostile subclass) simply
// replace the validation the superclass meant to perform. Call only
// private, final or static methods from readObject, just as constructors
// should (MET05-J).
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;

public class SER09_OverridableInReadObject {
    public static void main(String[] args) throws Exception {
        Object a = roundTrip(new SharedDocNoncompliant("plan.txt", "team-red"));
        System.out.println("noncompliant: " + a);

        Object b = roundTrip(new SharedDocCompliant("plan.txt", "team-red"));
        System.out.println("compliant:    " + b);
    }

    static Object roundTrip(Object o) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(o);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return in.readObject();
        }
    }
}

class DocNoncompliant implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    final String name;
    transient String accessNote;

    DocNoncompliant(String name) {
        this.name = name;
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        accessNote = describeAccess(); // noncompliant: overridable
    }

    String describeAccess() {
        return "private";
    }
}

class SharedDocNoncompliant extends DocNoncompliant {
    @Serial
    private static final long serialVersionUID = 1L;
    final String group;

    SharedDocNoncompliant(String name, String group) {
        super(name);
        this.group = group;
        accessNote = describeAccess();
    }

    @Override
    String describeAccess() {
        // Runs from the superclass's readObject before `group` is restored
        return group == null ? "everyone (group unknown)" : "group " + group;
    }

    @Override
    public String toString() {
        return name + " shared with " + accessNote + " [group field: " + group + "]";
    }
}

class DocCompliant implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    final String name;

    DocCompliant(String name) {
        this.name = name;
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        validateName(); // compliant: private
    }

    private void validateName() throws InvalidObjectException {
        if (name == null || name.isBlank()) throw new InvalidObjectException("name");
    }
}

class SharedDocCompliant extends DocCompliant {
    @Serial
    private static final long serialVersionUID = 1L;
    final String group;
    transient String accessNote;

    SharedDocCompliant(String name, String group) {
        super(name);
        this.group = group;
        accessNote = describeAccess(group);
    }

    // Each class finishes its own state in its own readObject
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (group == null) throw new InvalidObjectException("group");
        accessNote = describeAccess(group);
    }

    private static String describeAccess(String group) {
        return "group " + group;
    }

    @Override
    public String toString() {
        return name + " shared with " + accessNote + " [group field: " + group + "]";
    }
}

/* Output:
noncompliant: plan.txt shared with everyone (group unknown) [group field: team-red]
compliant:    plan.txt shared with group team-red [group field: team-red]
*/
