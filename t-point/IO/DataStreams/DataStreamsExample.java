// DataOutputStream / DataInputStream write and read Java primitives and
// strings in a fixed, portable binary format: big-endian ints and longs,
// IEEE 754 doubles, and writeUTF's length-prefixed "modified UTF-8".
//  - the reader must read the fields in exactly the order and types written;
//    the stream itself carries no field names or type tags
//  - reading past the end throws EOFException, a natural "end of records"
//  - sizes are fixed: an int is always 4 bytes, a long 8, a boolean 1
// This is how many binary file formats and network protocols are framed; a
// version field first lets the format evolve.
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

public class DataStreamsExample {
    record AuditRecord(long epochSecond, int userId, boolean success, String action) {}

    static final int MAGIC = 0xA0D17;   // identifies our file format
    static final short VERSION = 1;

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("data");
        Path file = dir.resolve("audit.bin");
        AuditRecord[] records = {
                new AuditRecord(1_693_000_000L, 1001, true, "login"),
                new AuditRecord(1_693_000_042L, 1002, false, "sudo"),
                new AuditRecord(1_693_000_100L, 1001, true, "logout"),
        };
        try {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
                out.writeInt(MAGIC);
                out.writeShort(VERSION);
                for (AuditRecord r : records) {
                    out.writeLong(r.epochSecond());
                    out.writeInt(r.userId());
                    out.writeBoolean(r.success());
                    out.writeUTF(r.action());
                }
            }
            byte[] bytes = Files.readAllBytes(file);
            System.out.println("file size: " + bytes.length + " bytes");
            System.out.println("header bytes: " + HexFormat.ofDelimiter(" ").formatHex(bytes, 0, 6));

            try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
                if (in.readInt() != MAGIC) throw new IOException("not an audit file");
                System.out.println("version: " + in.readShort());
                while (true) {
                    long when;
                    try {
                        when = in.readLong();
                    } catch (EOFException end) {
                        break; // no more records
                    }
                    AuditRecord r = new AuditRecord(when, in.readInt(), in.readBoolean(), in.readUTF());
                    System.out.println("  " + r);
                }
            }

            // Reading with the wrong types gives garbage, not an error
            try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
                in.readInt();
                in.readShort();
                System.out.println("first record read as int instead of long: " + in.readInt());
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}

/* Output:
file size: 66 bytes
header bytes: 00 0a 0d 17 00 01
version: 1
  AuditRecord[epochSecond=1693000000, userId=1001, success=true, action=login]
  AuditRecord[epochSecond=1693000042, userId=1002, success=false, action=sudo]
  AuditRecord[epochSecond=1693000100, userId=1001, success=true, action=logout]
first record read as int instead of long: 0
*/
