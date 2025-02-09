// NIO moves data between Channels (files, sockets) and ByteBuffers instead of
// streams and byte[]. A ByteBuffer has three cursors, 0 <= position <= limit
// <= capacity:
//  - put(...) writes at position and advances it
//  - flip() switches from filling to draining: limit = position, position = 0
//  - get(...) reads at position; compact() moves the unread bytes to the front
//    and switches back to filling; clear() forgets everything
//  - multi-byte values follow order(): BIG_ENDIAN by default (network order)
// FileChannel reads and writes buffers at a position, knows its size, can
// truncate, and does gathering writes (several buffers in one call, e.g. a
// header and a body) and scattering reads. transferTo copies channel to
// channel without passing the bytes through your code.
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HexFormat;

public class ChannelsExample {
    static String state(ByteBuffer b) {
        return "pos=" + b.position() + " lim=" + b.limit() + " cap=" + b.capacity();
    }

    public static void main(String[] args) throws IOException {
        // --- ByteBuffer cursors
        ByteBuffer buf = ByteBuffer.allocate(8);
        System.out.println("new:          " + state(buf));
        buf.put((byte) 'a').put((byte) 'b').put((byte) 'c');
        System.out.println("put 3 bytes:  " + state(buf));
        buf.flip();
        System.out.println("flip:         " + state(buf));
        System.out.println("get:          " + (char) buf.get() + ", " + state(buf));
        buf.compact(); // 'b','c' move to index 0..1, ready for more puts
        System.out.println("compact:      " + state(buf));
        buf.put((byte) 'd').flip();
        StringBuilder rest = new StringBuilder();
        while (buf.hasRemaining()) {
            rest.append((char) buf.get());
        }
        System.out.println("drained:      " + rest);

        // --- byte order and typed views
        ByteBuffer number = ByteBuffer.allocate(4).putInt(0x0A0B0C0D);
        ByteBuffer little = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0x0A0B0C0D);
        System.out.println("big-endian:    " + HexFormat.of().formatHex(number.array()));
        System.out.println("little-endian: " + HexFormat.of().formatHex(little.array()));
        ByteBuffer packet = ByteBuffer.wrap(new byte[] {0, 0, 0, 7, 0, 0, 1, 0});
        System.out.println("asIntBuffer:   " + packet.asIntBuffer().get(0) + ", " + packet.asIntBuffer().get(1));
        ByteBuffer slice = packet.slice(4, 4); // shares content, independent cursors
        slice.put(3, (byte) 9);
        System.out.println("slice writes through: packet[7] = " + packet.get(7));
        System.out.println("direct buffer: " + ByteBuffer.allocateDirect(16).isDirect()
                + ", heap buffer hasArray: " + ByteBuffer.allocate(16).hasArray());

        // --- FileChannel in a temp directory
        Path dir = Files.createTempDirectory("channels");
        Path file = dir.resolve("records.bin");
        try {
            byte[] body = "hello, channel".getBytes(StandardCharsets.UTF_8);
            ByteBuffer header = ByteBuffer.allocate(6).putShort((short) 0xCAFE).putInt(body.length).flip();
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                long written = ch.write(new ByteBuffer[] {header, ByteBuffer.wrap(body)}); // gathering write
                System.out.println("gathering write: " + written + " bytes, size " + ch.size());
            }
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
                ByteBuffer h = ByteBuffer.allocate(6);
                ByteBuffer b = ByteBuffer.allocate((int) ch.size() - 6);
                ch.read(new ByteBuffer[] {h, b}); // scattering read fills h, then b
                h.flip();
                b.flip();
                System.out.println("magic " + Integer.toHexString(h.getShort() & 0xFFFF) + ", length " + h.getInt()
                        + ", body \"" + StandardCharsets.UTF_8.decode(b) + "\"");

                // positional read: does not move the channel's own position
                ByteBuffer word = ByteBuffer.allocate(7);
                ch.read(word, 13);
                System.out.println("read at offset 13: \"" + new String(word.array(), StandardCharsets.UTF_8)
                        + "\", channel position still " + ch.position());
            }
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.WRITE)) {
                ch.truncate(11);
                System.out.println("truncated to " + ch.size() + " bytes");
            }
            Path copy = dir.resolve("copy.bin");
            try (FileChannel in = FileChannel.open(file, StandardOpenOption.READ);
                 FileChannel out = FileChannel.open(copy, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                long moved = 0;
                while (moved < in.size()) { // transferTo may move fewer bytes than asked
                    moved += in.transferTo(moved, in.size() - moved, out);
                }
                System.out.println("transferTo copied " + moved + " bytes, tail \""
                        + new String(Files.readAllBytes(copy), 6, 5, StandardCharsets.UTF_8) + "\"");
            }
            Files.delete(copy);
        } finally {
            Files.deleteIfExists(file);
            Files.delete(dir);
        }

        // --- any InputStream can be adapted to a channel
        try (ReadableByteChannel ch = Channels.newChannel(new ByteArrayInputStream(new byte[100]))) {
            ByteBuffer chunk = ByteBuffer.allocate(32);
            int reads = 0;
            int total = 0;
            for (int n; (n = ch.read(chunk)) != -1; chunk.clear()) {
                reads++;
                total += n;
            }
            System.out.println("read 100 bytes in " + reads + " chunks of up to 32: total " + total);
        }
    }
}

/* Output:
new:          pos=0 lim=8 cap=8
put 3 bytes:  pos=3 lim=8 cap=8
flip:         pos=0 lim=3 cap=8
get:          a, pos=1 lim=3 cap=8
compact:      pos=2 lim=8 cap=8
drained:      bcd
big-endian:    0a0b0c0d
little-endian: 0d0c0b0a
asIntBuffer:   7, 256
slice writes through: packet[7] = 9
direct buffer: true, heap buffer hasArray: true
gathering write: 20 bytes, size 20
magic cafe, length 14, body "hello, channel"
read at offset 13: "channel", channel position still 20
truncated to 11 bytes
transferTo copied 11 bytes, tail "hello"
read 100 bytes in 4 chunks of up to 32: total 100
*/
