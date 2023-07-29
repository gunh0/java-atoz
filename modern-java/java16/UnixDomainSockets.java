// JEP 380 — Unix-Domain Socket Channels (Java 16)
//
// Processes on the same host often talk over TCP on localhost, but a
// loopback port is reachable by every local user and process, needs a free
// port number, and goes through the TCP/IP stack. Unix-domain sockets are
// addressed by a file-system path instead: access is controlled by file
// permissions, there is no port to collide on or to expose by accident,
// and they are faster for local IPC (Docker, PostgreSQL and systemd use
// them). Java 16 lets SocketChannel and ServerSocketChannel use them via
// StandardProtocolFamily.UNIX and UnixDomainSocketAddress; the rest of the
// NIO channel API is unchanged. (Windows 10+ supports them too.)
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class UnixDomainSockets {
    public static void main(String[] args) throws Exception {
        // Before: TCP on the loopback interface, any local process can connect to the port
        try (ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            try (SocketChannel client = SocketChannel.open(server.getLocalAddress());
                 SocketChannel accepted = server.accept()) {
                send(client, "ping over tcp");
                System.out.println("before: received '" + receive(accepted) + "' via "
                        + server.getLocalAddress().getClass().getSimpleName());
            }
        }

        // After: the same channels, addressed by a path in a private temp directory
        Path dir = Files.createTempDirectory("uds");
        Path socketFile = dir.resolve("app.sock");
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketFile);
        try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            server.bind(address); // creates the socket file
            System.out.println("after: socket file exists = " + Files.exists(socketFile)
                    + ", name = " + socketFile.getFileName());

            try (SocketChannel client = SocketChannel.open(address);
                 SocketChannel accepted = server.accept()) {
                send(client, "ping over a unix socket");
                System.out.println("after: server received '" + receive(accepted) + "'");
                send(accepted, "pong");
                System.out.println("after: client received '" + receive(client) + "'");
                System.out.println("after: remote address type = "
                        + client.getRemoteAddress().getClass().getSimpleName());
            }
        } finally {
            // Closing the channel does NOT remove the file; clean up explicitly
            Files.deleteIfExists(socketFile);
            Files.delete(dir);
        }

        // A leftover socket file would make the next bind() to that path fail
        System.out.println("after: socket file removed = " + !Files.exists(socketFile));
    }

    static void send(SocketChannel ch, String msg) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
        while (buf.hasRemaining()) {
            ch.write(buf);
        }
    }

    static String receive(SocketChannel ch) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(256);
        ch.read(buf);
        buf.flip();
        return StandardCharsets.UTF_8.decode(buf).toString();
    }
}

/* Output:
before: received 'ping over tcp' via InetSocketAddress
after: socket file exists = true, name = app.sock
after: server received 'ping over a unix socket'
after: client received 'pong'
after: remote address type = UnixDomainSocketAddress
after: socket file removed = true
*/
