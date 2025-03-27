// SER10-J. Avoid memory and resource leaks during serialization
// https://wiki.sei.cmu.edu/confluence/display/java/SER10-J.+Avoid+memory+and+resource+leaks+during+serialization
//
// ObjectOutputStream remembers every object it has written in a handle
// table, so that a second write of the same object becomes a 5-byte
// back-reference instead of a full copy. On a long-lived stream (a socket
// that carries one message after another) that table has two effects:
//  - nothing written is ever released, so the sender's heap grows with
//    every message until OutOfMemoryError (and ObjectInputStream keeps a
//    matching table on the receiving side);
//  - writing the SAME object again after mutating it sends only the
//    back-reference, and the receiver silently sees the old state.
// Call reset() after each logical message (or use writeUnshared for a
// top-level object whose identity must not be shared) on long-lived streams.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SER10_SerializationLeaks {
    static final class Quote implements Serializable {
        private static final long serialVersionUID = 1L;
        final String symbol;
        long priceCents;

        Quote(String symbol, long priceCents) {
            this.symbol = symbol;
            this.priceCents = priceCents;
        }
    }

    interface Writer {
        void write(ObjectOutputStream out, Quote q) throws IOException;
    }

    // One "connection": the same quote object is updated and sent three times
    static void stream(String label, Writer writer) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        List<Integer> sizes = new ArrayList<>();
        try (ObjectOutputStream out = new ObjectOutputStream(wire)) {
            Quote quote = new Quote("ACME", 100_00);
            for (long price : new long[] {100_00, 101_50, 99_25}) {
                quote.priceCents = price;
                out.flush();
                int before = wire.size();
                writer.write(out, quote);
                out.flush();
                sizes.add(wire.size() - before);
            }
        }
        List<Long> received = new ArrayList<>();
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(wire.toByteArray()))) {
            for (int i = 0; i < 3; i++) {
                received.add(((Quote) in.readObject()).priceCents);
            }
        }
        System.out.println(label);
        System.out.println("  sent prices:     [10000, 10150, 9925]");
        System.out.println("  received prices: " + received);
        System.out.println("  bytes per write: " + sizes);
    }

    public static void main(String[] args) throws Exception {
        // Noncompliant: writeObject on a long-lived stream, no reset
        stream("noncompliant writeObject:", (out, q) -> out.writeObject(q));

        // Compliant: reset() after each message clears the handle table
        stream("compliant writeObject + reset():", (out, q) -> {
            out.writeObject(q);
            out.reset();
        });

        // Compliant for a top-level object: writeUnshared never emits a back-reference to it
        stream("compliant writeUnshared:", (out, q) -> out.writeUnshared(q));

        // The leak: 10,000 distinct messages, then the first one is written again.
        // A back-reference (a few bytes) proves the stream still holds message #1,
        // and with it all 10,000 objects, for as long as the stream lives.
        for (boolean reset : new boolean[] {false, true}) {
            ByteArrayOutputStream wire = new ByteArrayOutputStream();
            try (ObjectOutputStream out = new ObjectOutputStream(wire)) {
                Quote first = new Quote("Q0", 0);
                out.writeObject(first);
                for (int i = 1; i < 10_000; i++) {
                    out.writeObject(new Quote("Q" + i, i));
                    if (reset) out.reset();
                }
                out.flush();
                int before = wire.size();
                out.writeObject(first);
                out.flush();
                int size = wire.size() - before;
                System.out.println((reset ? "with reset():    " : "without reset(): ")
                        + "re-writing message #1 costs " + size + " bytes -> "
                        + (size <= 5 ? "still referenced by the stream" : "full copy, handle table was cleared"));
            }
        }
    }
}

/* Output:
noncompliant writeObject:
  sent prices:     [10000, 10150, 9925]
  received prices: [10000, 10000, 10000]
  bytes per write: [105, 5, 5]
compliant writeObject + reset():
  sent prices:     [10000, 10150, 9925]
  received prices: [10000, 10150, 9925]
  bytes per write: [106, 106, 106]
compliant writeUnshared:
  sent prices:     [10000, 10150, 9925]
  received prices: [10000, 10150, 9925]
  bytes per write: [105, 19, 19]
without reset(): re-writing message #1 costs 5 bytes -> still referenced by the stream
with reset():    re-writing message #1 costs 103 bytes -> full copy, handle table was cleared
*/
