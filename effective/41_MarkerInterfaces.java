// Effective Java, Item 41: Use marker interfaces to define types.
//
// A marker interface has no methods; implementing it just says "this class
// has property X". A marker annotation can say the same thing, but only a
// marker interface defines a type, so a method that requires the property can
// demand it in its signature and the compiler enforces it. Serializable is
// the cautionary tale: ObjectOutputStream.writeObject takes Object, so passing
// a non-serializable object fails only at run time. Prefer a marker
// annotation when the mark applies to methods, fields or other non-class
// elements, or when you are inside an annotation-heavy framework; prefer a
// marker interface whenever you would want to write "void m(Marked x)".
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class MarkerInterfaces {
    public static void main(String[] args) throws IOException {
        // Serializable is a marker interface, but writeObject(Object) does not use the type
        try (ObjectOutputStream out = new ObjectOutputStream(new ByteArrayOutputStream())) {
            out.writeObject(new Invoice(100));
            System.out.println("Invoice written");
            out.writeObject(new Session("admin"));
        } catch (java.io.NotSerializableException e) {
            System.out.println("Session rejected only at run time: NotSerializableException " + e.getMessage());
        }

        // Marker annotation: checked by reflection, again at run time
        Auditor auditor = new Auditor();
        auditor.recordReflectively(new Invoice(250));
        auditor.recordReflectively(new Session("guest"));

        // Marker interface used as a parameter type: wrong arguments do not compile
        auditor.record(new Invoice(300));
        // auditor.record(new Session("root")); // error: Session cannot be converted to Auditable
        System.out.println("record(Auditable) accepted only Auditable types at compile time");
    }
}

/** Marker interface: an auditable record. Defines a type. */
interface Auditable {
}

/** Marker annotation: the same idea, but no type. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface AuditableRecord {
}

@AuditableRecord
class Invoice implements Auditable, Serializable {
    private static final long serialVersionUID = 1L;
    final int amount;

    Invoice(int amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "Invoice(" + amount + ")";
    }
}

class Session {
    final String user;

    Session(String user) {
        this.user = user;
    }

    @Override
    public String toString() {
        return "Session(" + user + ")";
    }
}

class Auditor {
    void recordReflectively(Object o) {
        if (!o.getClass().isAnnotationPresent(AuditableRecord.class)) {
            System.out.println("annotation check failed at run time for " + o);
            return;
        }
        System.out.println("audited (annotation) " + o);
    }

    void record(Auditable a) {
        System.out.println("audited (interface) " + a);
    }
}

/* Output:
Invoice written
Session rejected only at run time: NotSerializableException Session
audited (annotation) Invoice(250)
annotation check failed at run time for Session(guest)
audited (interface) Invoice(300)
record(Auditable) accepted only Auditable types at compile time
*/
