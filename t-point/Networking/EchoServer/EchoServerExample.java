// TCP sockets with java.net:
//  - ServerSocket binds a port and accept() blocks until a client connects,
//    returning a Socket for that one connection
//  - Socket connects to host:port; each side reads getInputStream() and
//    writes getOutputStream(), here wrapped in reader/writer for text lines
// The server binds to the loopback address only (never 0.0.0.0 for a test
// service) and to port 0, so the OS picks a free ephemeral port. It handles
// each client on its own thread; the client sets a read timeout so a broken
// server cannot hang it forever. Everything is closed by try-with-resources.
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class EchoServerExample {
    static final AtomicInteger served = new AtomicInteger();

    // Server side: one connection, line-based protocol, "bye" ends it
    static void handle(Socket client) {
        try (client;
             BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(client.getOutputStream(), true, StandardCharsets.UTF_8)) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals("bye")) {
                    out.println("closing");
                    break;
                }
                out.println("echo: " + line.toUpperCase(Locale.ROOT));
            }
            served.incrementAndGet();
        } catch (IOException e) {
            System.out.println("server error: " + e.getMessage());
        }
    }

    // Client side: send lines, print each reply
    static void talk(int port, String name, List<String> lines) throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            socket.setSoTimeout(2_000);
            for (String line : lines) {
                out.println(line);
                System.out.printf("%s > %-7s < %s%n", name, line, in.readLine());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ExecutorService workers = Executors.newCachedThreadPool();
        try (ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            System.out.println("listening on loopback: " + server.getInetAddress().isLoopbackAddress()
                    + ", ephemeral port: " + (server.getLocalPort() > 0));

            Thread acceptor = new Thread(() -> {
                while (!server.isClosed()) {
                    try {
                        Socket client = server.accept();
                        workers.submit(() -> handle(client));
                    } catch (SocketException closed) {
                        return; // server.close() unblocks accept()
                    } catch (IOException e) {
                        System.out.println("accept failed: " + e.getMessage());
                    }
                }
            });
            acceptor.start();

            // Clients run one after another so the printed transcript is deterministic
            talk(server.getLocalPort(), "client-1", List.of("hello", "status", "bye"));
            talk(server.getLocalPort(), "client-2", List.of("ping", "bye"));
            server.close();
            acceptor.join();
        }
        workers.shutdown();
        workers.awaitTermination(2, TimeUnit.SECONDS);
        System.out.println("connections served: " + served.get());
    }
}

/* Output:
listening on loopback: true, ephemeral port: true
client-1 > hello   < echo: HELLO
client-1 > status  < echo: STATUS
client-1 > bye     < closing
client-2 > ping    < echo: PING
client-2 > bye     < closing
connections served: 2
*/
