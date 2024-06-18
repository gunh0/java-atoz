// FIO10-J. Ensure the array is filled when using read() to fill an array
// https://wiki.sei.cmu.edu/confluence/display/java/FIO10-J.+Ensure+the+array+is+filled+when+using+read()+to+fill+an+array
//
// InputStream.read(byte[]) and Reader.read(char[]) return as soon as SOME
// data is available, not when the array is full. With files they often
// fill the array, which hides the bug in tests; with sockets, pipes,
// decompressing or decrypting streams they routinely return less. Code
// that ignores the count then works on a half-filled buffer: zeros where
// the data should be, a truncated key or MAC, a misparsed header. Loop
// until the array is full, or use readNBytes(n) / readFully, which do it
// for you and report end of stream properly.
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HexFormat;

public class FIO10_FillTheArray {
    static final byte[] MESSAGE = HexFormat.of().parseHex("00112233445566778899aabbccddeeff");

    public static void main(String[] args) throws IOException {
        // Noncompliant: one read() call, count ignored
        byte[] buf = new byte[16];
        try (InputStream in = new TrickleStream(MESSAGE, 5)) {
            in.read(buf);
        }
        System.out.println("noncompliant read():      " + HexFormat.of().formatHex(buf));

        // Compliant: readNBytes loops until n bytes or end of stream
        try (InputStream in = new TrickleStream(MESSAGE, 5)) {
            byte[] all = in.readNBytes(16);
            System.out.println("compliant readNBytes(16): " + HexFormat.of().formatHex(all));
        }

        // Compliant: DataInputStream.readFully also throws if the stream ends early
        try (DataInputStream in = new DataInputStream(new TrickleStream(MESSAGE, 5))) {
            byte[] tag = new byte[16];
            in.readFully(tag);
            System.out.println("compliant readFully:      " + HexFormat.of().formatHex(tag));
        }
        try (DataInputStream in = new DataInputStream(new TrickleStream(new byte[10], 5))) {
            in.readFully(new byte[16]);
        } catch (EOFException e) {
            System.out.println("compliant readFully on a 10-byte stream: EOFException");
        }

        // Compliant: a hand-written loop that honours the return value
        byte[] manual = new byte[16];
        try (InputStream in = new TrickleStream(MESSAGE, 5)) {
            int off = 0;
            while (off < manual.length) {
                int n = in.read(manual, off, manual.length - off);
                if (n < 0) throw new EOFException();
                off += n;
            }
        }
        System.out.println("compliant loop:           " + HexFormat.of().formatHex(manual));
    }
}

// Behaves like a network stream: at most `chunk` bytes per read call
class TrickleStream extends ByteArrayInputStream {
    private final int chunk;

    TrickleStream(byte[] data, int chunk) {
        super(data);
        this.chunk = chunk;
    }

    @Override
    public synchronized int read(byte[] b, int off, int len) {
        return super.read(b, off, Math.min(len, chunk));
    }

    @Override
    public int read(byte[] b) {
        return read(b, 0, b.length);
    }
}

/* Output:
noncompliant read():      00112233440000000000000000000000
compliant readNBytes(16): 00112233445566778899aabbccddeeff
compliant readFully:      00112233445566778899aabbccddeeff
compliant readFully on a 10-byte stream: EOFException
compliant loop:           00112233445566778899aabbccddeeff
*/
