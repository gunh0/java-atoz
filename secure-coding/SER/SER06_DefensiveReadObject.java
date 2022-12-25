// SER06-J. Make defensive copies of private mutable components during
// deserialization
// https://wiki.sei.cmu.edu/confluence/display/java/SER06-J.+Make+defensive+copies+of+private+mutable+components+during+deserialization
//
// readObject is effectively a public constructor that takes a byte stream,
// and the stream is attacker-controlled. Besides arbitrary field values, a
// stream may contain extra back-references (TC_REFERENCE) to objects already
// read, including the private mutable components of the object being
// deserialized. An attacker who appends such references gets a handle on
// the internal Date objects of a supposedly immutable Period and can change
// them at will, bypassing the constructor's checks. A readObject method must
// do what a constructor does: copy every mutable component, then validate
// the invariants (and throw InvalidObjectException when they fail).
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Date;

public class SER06_DefensiveReadObject {
    public static void main(String[] args) throws Exception {
        PeriodNoncompliant bad = attack(new PeriodNoncompliant(new Date(0), new Date(1_000)));
        System.out.println("noncompliant: " + bad + " (start after end!)");

        PeriodCompliant good = attack(new PeriodCompliant(new Date(0), new Date(1_000)));
        System.out.println("compliant:    " + good);
    }

    // Serialize a period, then append two back-references to its internal
    // Date objects (stream handles 4 and 5), then read everything back.
    @SuppressWarnings("unchecked")
    static <T> T attack(T period) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(period);
        out.flush();
        byte[] ref = {0x71, 0, 0x7e, 0, 5}; // TC_REFERENCE to handle 0x7e0005: the 'start' Date
        bos.write(ref);
        ref[4] = 4;                         // handle 0x7e0004: the 'end' Date
        bos.write(ref);

        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
        T result = (T) in.readObject();
        Date start = (Date) in.readObject();
        Date end = (Date) in.readObject();
        // The attacker now changes the "private" dates of the deserialized period
        start.setTime(5_000);
        end.setTime(2_000);
        return result;
    }
}

final class PeriodNoncompliant implements Serializable {
    private static final long serialVersionUID = 1L;
    private Date start;
    private Date end;

    PeriodNoncompliant(Date start, Date end) {
        this.start = new Date(start.getTime());
        this.end = new Date(end.getTime());
        if (this.start.after(this.end)) {
            throw new IllegalArgumentException("start after end");
        }
    }

    // No readObject: default deserialization keeps the stream's Date objects

    @Override
    public String toString() {
        return "Period[start=" + start.getTime() + ", end=" + end.getTime() + "]";
    }
}

final class PeriodCompliant implements Serializable {
    private static final long serialVersionUID = 1L;
    private Date start; // not final: readObject must replace them
    private Date end;

    PeriodCompliant(Date start, Date end) {
        this.start = new Date(start.getTime());
        this.end = new Date(end.getTime());
        if (this.start.after(this.end)) {
            throw new IllegalArgumentException("start after end");
        }
    }

    private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
        s.defaultReadObject();
        // Defensive copies first, so no one else holds our Date objects...
        start = new Date(start.getTime());
        end = new Date(end.getTime());
        // ...then validate the invariants, exactly like the constructor
        if (start.after(end)) {
            throw new InvalidObjectException("start after end");
        }
    }

    @Override
    public String toString() {
        return "Period[start=" + start.getTime() + ", end=" + end.getTime() + "]";
    }
}

/* Output:
noncompliant: Period[start=5000, end=2000] (start after end!)
compliant:    Period[start=0, end=1000]
*/
