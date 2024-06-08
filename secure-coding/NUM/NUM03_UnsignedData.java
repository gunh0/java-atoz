// NUM03-J. Use integer types that can fully represent the possible range of unsigned data
// https://wiki.sei.cmu.edu/confluence/display/java/NUM03-J.+Use+integer+types+that+can+fully+represent+the+possible+range+of+unsigned+data
//
// Java's byte, short, int and long are signed, but file formats and
// network protocols are full of unsigned fields: a uint8 type code, a
// uint16 port, a uint32 length. Read a uint32 0xFFFFFFF0 into an int and
// it becomes -16, which sails through a check like `len <= MAX_LEN` and
// then breaks array allocation, offsets or loop bounds. Read unsigned
// values into a wider type (readUnsignedByte/readUnsignedShort,
// Byte.toUnsignedInt, Integer.toUnsignedLong), or use the
// Integer.compareUnsigned / divideUnsigned family when you must keep them
// in an int.
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class NUM03_UnsignedData {
    static final long MAX_LEN = 1 << 20;

    public static void main(String[] args) throws IOException {
        // A message header from the wire: uint8 type, uint16 port, uint32 length
        byte[] header = ByteBuffer.allocate(7)
                .put((byte) 0xC8)            // type 200
                .putShort((short) 0xFFFE)    // port 65534
                .putInt(0xFFFF_FFF0)         // length 4294967280
                .array();

        // Noncompliant: signed reads
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(header))) {
            byte type = in.readByte();
            short port = in.readShort();
            int length = in.readInt();
            System.out.println("noncompliant: type " + type + ", port " + port + ", length " + length);
            System.out.println("noncompliant: length <= MAX_LEN? " + (length <= MAX_LEN) + "  <- the check passes");
        }

        // Compliant: widen while reading
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(header))) {
            int type = in.readUnsignedByte();
            int port = in.readUnsignedShort();
            long length = Integer.toUnsignedLong(in.readInt());
            System.out.println("compliant:    type " + type + ", port " + port + ", length " + length);
            System.out.println("compliant:    length <= MAX_LEN? " + (length <= MAX_LEN));
        }

        // Unsigned helpers when the value has to stay in an int
        int raw = 0xFFFF_FFF0;
        System.out.println("Integer.toUnsignedString: " + Integer.toUnsignedString(raw));
        System.out.println("signed compare(raw, 16): " + Integer.signum(Integer.compare(raw, 16))
                + ", compareUnsigned(raw, 16): " + Integer.signum(Integer.compareUnsigned(raw, 16)));
        System.out.println("raw / 16 signed: " + raw / 16 + ", divideUnsigned: " + Integer.divideUnsigned(raw, 16));

        // Bytes from a buffer: & 0xFF or Byte.toUnsignedInt
        byte b = (byte) 0xC8;
        System.out.println("byte 0xC8 as int: " + (int) b + ", unsigned: " + Byte.toUnsignedInt(b));
    }
}

/* Output:
noncompliant: type -56, port -2, length -16
noncompliant: length <= MAX_LEN? true  <- the check passes
compliant:    type 200, port 65534, length 4294967280
compliant:    length <= MAX_LEN? false
Integer.toUnsignedString: 4294967280
signed compare(raw, 16): -1, compareUnsigned(raw, 16): 1
raw / 16 signed: -1, divideUnsigned: 268435455
byte 0xC8 as int: -56, unsigned: 200
*/
