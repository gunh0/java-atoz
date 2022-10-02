// Decorator (structural): attach additional responsibilities to an object
// dynamically by wrapping it in another object with the same interface.
//
// Decorators are a flexible alternative to subclassing: each wrapper adds one
// behaviour and forwards the rest, and wrappers can be stacked in any order at
// runtime. Here an audit-log writer is wrapped to mask secrets, add a
// sequence number and keep a count, without any of these knowing each other.
// JDK example: java.io is built this way. BufferedOutputStream, GZIPOutputStream,
// DataOutputStream, CheckedInputStream ... all wrap another stream and are
// streams themselves (FilterInputStream / FilterOutputStream are the base).
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class DecoratorDemo {
    // The component interface
    interface LogWriter {
        void write(String line);
    }

    // Concrete component: actually stores the line
    static final class MemoryLogWriter implements LogWriter {
        final List<String> lines = new ArrayList<>();
        public void write(String line) { lines.add(line); }
    }

    // Base decorator: holds the wrapped writer and forwards by default
    abstract static class LogWriterDecorator implements LogWriter {
        protected final LogWriter inner;
        LogWriterDecorator(LogWriter inner) { this.inner = inner; }
        public void write(String line) { inner.write(line); }
    }

    // Masks anything that looks like a password=... or an API key
    static final class MaskingWriter extends LogWriterDecorator {
        private static final Pattern SECRET = Pattern.compile("(password=|api_key=)\\S+");
        MaskingWriter(LogWriter inner) { super(inner); }
        @Override
        public void write(String line) { inner.write(SECRET.matcher(line).replaceAll("$1****")); }
    }

    // Prefixes a sequence number (a deterministic stand-in for a timestamp)
    static final class SequenceWriter extends LogWriterDecorator {
        private int next = 1;
        SequenceWriter(LogWriter inner) { super(inner); }
        @Override
        public void write(String line) { inner.write(String.format("#%03d %s", next++, line)); }
    }

    // Adds behaviour without changing the line at all
    static final class CountingWriter extends LogWriterDecorator {
        int count;
        CountingWriter(LogWriter inner) { super(inner); }
        @Override
        public void write(String line) { count++; super.write(line); }
    }

    public static void main(String[] args) throws IOException {
        MemoryLogWriter store = new MemoryLogWriter();
        // Order matters: mask first, then number, so numbers are never masked
        CountingWriter counter = new CountingWriter(new SequenceWriter(new MaskingWriter(store)));
        LogWriter log = counter;

        log.write("login user=alice password=hunter2 result=ok");
        log.write("config reload api_key=sk_live_123456 by=deploy-bot");
        log.write("logout user=alice");
        store.lines.forEach(System.out::println);
        System.out.println("lines written: " + counter.count);

        // The same idea in java.io: stack decorators around a byte array stream
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new GZIPOutputStream(bytes)))) {
            out.writeUTF("audit-2022-10.log");
            out.writeInt(3);
            out.writeLong(1_664_668_800L);
        }

        CRC32 crc = new CRC32();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new CheckedInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes.toByteArray())), crc)))) {
            System.out.println("read back: " + in.readUTF() + ", " + in.readInt() + ", " + in.readLong());
        }
        System.out.printf("CRC32 of the decompressed bytes: %08x%n", crc.getValue());
    }
}

/* Output:
#001 login user=alice password=**** result=ok
#002 config reload api_key=**** by=deploy-bot
#003 logout user=alice
lines written: 3
read back: audit-2022-10.log, 3, 1664668800
CRC32 of the decompressed bytes: 93e50f16
*/
