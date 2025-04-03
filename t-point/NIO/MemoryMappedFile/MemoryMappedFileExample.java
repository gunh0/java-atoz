// FileChannel.map(mode, position, size) maps a region of a file into memory:
// reads and writes on the returned MappedByteBuffer go to the OS page cache
// directly, with no read()/write() calls and no copying into the Java heap.
// Good for large files read at random offsets (indexes, fixed-size records).
//  - READ_WRITE: changes reach the file (force() flushes them to the device)
//  - READ_ONLY:  any put throws ReadOnlyBufferException
//  - PRIVATE:    copy-on-write; changes stay in this mapping only
//  - mapping beyond the end of a READ_WRITE file grows the file
// A MappedByteBuffer is unmapped only when it is garbage collected, so the
// file stays mapped (and on some systems cannot be deleted) for an unknown
// time. Mapping into a MemorySegment with an Arena (Java 22) unmaps
// deterministically when the arena is closed.
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class MemoryMappedFileExample {
    static final int RECORD = 12; // int id + long balance
    static final int COUNT = 1000;

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("mmap");
        Path file = dir.resolve("accounts.dat");
        try {
            // Create and fill a file of 1000 fixed-size records through a mapping
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                System.out.println("size before map: " + ch.size());
                MappedByteBuffer map = ch.map(MapMode.READ_WRITE, 0, (long) RECORD * COUNT);
                System.out.println("size after map:  " + ch.size() + " (the mapping grew the file)");
                for (int i = 0; i < COUNT; i++) {
                    map.putInt(i * RECORD, i).putLong(i * RECORD + 4, i * 100L);
                }
                map.force(); // flush to the storage device
            }

            // Random access: update record 742 in place
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                MappedByteBuffer map = ch.map(MapMode.READ_WRITE, 742L * RECORD, RECORD); // just one record
                map.putLong(4, map.getLong(4) + 5);
            }
            // ... and see it with ordinary file I/O
            byte[] all = Files.readAllBytes(file);
            ByteBuffer plain = ByteBuffer.wrap(all);
            System.out.println("record 742 via Files.readAllBytes: id " + plain.getInt(742 * RECORD)
                    + ", balance " + plain.getLong(742 * RECORD + 4));

            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
                MappedByteBuffer ro = ch.map(MapMode.READ_ONLY, 0, ch.size());
                long total = 0;
                for (int i = 0; i < COUNT; i++) {
                    total += ro.getLong(i * RECORD + 4);
                }
                System.out.println("sum of balances: " + total + ", read-only: " + ro.isReadOnly());
                try {
                    ro.putInt(0, -1);
                } catch (ReadOnlyBufferException e) {
                    System.out.println("put on READ_ONLY mapping: ReadOnlyBufferException");
                }
            }

            // PRIVATE: copy-on-write, the file is not changed
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                MappedByteBuffer priv = ch.map(MapMode.PRIVATE, 0, RECORD);
                priv.putLong(4, 999_999L);
                System.out.println("PRIVATE mapping sees " + priv.getLong(4) + ", file still has "
                        + ByteBuffer.wrap(Files.readAllBytes(file)).getLong(4));
            }

            // Deterministic unmapping with an Arena-backed MemorySegment
            MemorySegment escaped;
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ);
                 Arena arena = Arena.ofConfined()) {
                MemorySegment seg = ch.map(MapMode.READ_ONLY, 0, ch.size(), arena);
                // the file holds big-endian values; JAVA_LONG_UNALIGNED uses native order
                ValueLayout.OfLong bigEndianLong = ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
                System.out.println("segment size " + seg.byteSize() + ", record 999 balance "
                        + seg.get(bigEndianLong, 999L * RECORD + 4));
                escaped = seg;
            } // arena closed: the region is unmapped right here
            try {
                escaped.get(ValueLayout.JAVA_BYTE, 0);
            } catch (IllegalStateException e) {
                System.out.println("access after the arena closed: IllegalStateException");
            }
        } finally {
            Files.deleteIfExists(file);
            Files.delete(dir);
        }
    }
}

/* Output:
size before map: 0
size after map:  12000 (the mapping grew the file)
record 742 via Files.readAllBytes: id 742, balance 74205
sum of balances: 49950005, read-only: true
put on READ_ONLY mapping: ReadOnlyBufferException
PRIVATE mapping sees 999999, file still has 0
segment size 12000, record 999 balance 99900
access after the arena closed: IllegalStateException
*/
