// Effective Java, Item 88: Write readObject methods defensively.
//
// readObject is effectively a public constructor that takes a byte stream as
// its argument, and the bytes need not come from a real instance. It must
// therefore do everything a constructor does: check invariants (throwing
// InvalidObjectException) and make defensive copies of mutable components.
// Two in-process demonstrations against an immutable-looking Period class:
//   1. a hand-edited stream with end before start, which skips the
//      constructor's check;
//   2. extra back-references appended to a stream, which hand the reader
//      the Period's own private Date objects to mutate later.
// The defensive readObject copies first, then validates, and defeats both.
// (Never use the default readObject if you would not accept every possible
// field combination as a constructor argument.)
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

public class DefensiveReadObject {
    // Constructor and accessors are defensive, deserialization is not
    static class UnsafePeriod implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        Date start, end; // non-final so Period.readObject can replace them with copies

        UnsafePeriod(Date start, Date end) {
            this.start = new Date(start.getTime());
            this.end = new Date(end.getTime());
            if (this.start.compareTo(this.end) > 0) {
                throw new IllegalArgumentException(start + " after " + end);
            }
        }

        Date start() {
            return new Date(start.getTime());
        }

        Date end() {
            return new Date(end.getTime());
        }

        @Override
        public String toString() {
            return "[" + start.getTime() + " .. " + end.getTime() + "]";
        }
    }

    static final class Period extends UnsafePeriod {
        @Serial
        private static final long serialVersionUID = 1L;

        Period(Date start, Date end) {
            super(start, end);
        }

        @Serial
        private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
            s.defaultReadObject();
            // Copy first, so nobody else holds a reference to our Dates...
            start = new Date(start.getTime());
            end = new Date(end.getTime());
            // ...then check the invariant on the copies
            if (start.compareTo(end) > 0) {
                throw new InvalidObjectException(start.getTime() + " after " + end.getTime());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        UnsafePeriod[] samples = {
                new UnsafePeriod(new Date(1000), new Date(2000)),
                new Period(new Date(1000), new Date(2000)),
        };
        for (UnsafePeriod p : samples) {
            String name = p.getClass().getSimpleName();

            // 1. Bogus stream: swap the two serialized long timestamps
            byte[] bytes = serialize(p);
            int i1000 = indexOfLong(bytes, 1000), i2000 = indexOfLong(bytes, 2000);
            writeLong(bytes, i1000, 2000);
            writeLong(bytes, i2000, 1000);
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                System.out.println(name + " from edited stream: " + in.readObject() + "  <- end before start");
            } catch (InvalidObjectException e) {
                System.out.println(name + " from edited stream: InvalidObjectException: " + e.getMessage());
            }

            // 2. Rogue references: append back-references to the two Date objects.
            // Handle numbers: 0 class desc, 1 field type string, 2 the period,
            // then fields in name order: 3 Date class desc, 4 end, 5 start (one
            // more desc for the subclass shifts them by one).
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
                out.writeObject(p);
            }
            int base = p instanceof Period ? 6 : 5; // handle of the start Date
            bos.write(new byte[] {0x71, 0, 0x7e, 0, (byte) base});
            bos.write(new byte[] {0x71, 0, 0x7e, 0, (byte) (base - 1)});
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
                UnsafePeriod period = (UnsafePeriod) in.readObject();
                Date start = (Date) in.readObject();
                Date end = (Date) in.readObject();
                start.setTime(9000); // mutate the "immutable" period from outside
                end.setTime(5000);
                System.out.println(name + " after mutating stolen Dates: " + period);
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

    static int indexOfLong(byte[] b, long v) {
        outer:
        for (int i = 0; i + 8 <= b.length; i++) {
            for (int k = 0; k < 8; k++) {
                if (b[i + k] != (byte) (v >>> (56 - 8 * k))) {
                    continue outer;
                }
            }
            return i;
        }
        throw new IllegalStateException("not found: " + v);
    }

    static void writeLong(byte[] b, int at, long v) {
        for (int k = 0; k < 8; k++) {
            b[at + k] = (byte) (v >>> (56 - 8 * k));
        }
    }
}

/* Output:
UnsafePeriod from edited stream: [2000 .. 1000]  <- end before start
UnsafePeriod after mutating stolen Dates: [9000 .. 5000]
Period from edited stream: InvalidObjectException: 2000 after 1000
Period after mutating stolen Dates: [1000 .. 2000]
*/
