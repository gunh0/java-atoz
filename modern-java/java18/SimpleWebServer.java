// JEP 408 — Simple Web Server (Java 18)
//
// Serving a directory of static files for a quick test, a demo or a docs
// preview used to mean installing something else (python -m http.server,
// nginx) or wiring com.sun.net.httpserver handlers by hand. Java 18 ships a
// minimal static file server: the `jwebserver` command line tool and, for
// use inside programs and tests, the SimpleFileServer API that builds an
// HttpServer, an HttpHandler or a logging Filter for a directory. It serves
// GET and HEAD only, over HTTP/1.1 without TLS, and is meant for testing and
// development, not production. This program serves a temporary directory on
// an ephemeral loopback port and requests a few paths from it.
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import com.sun.net.httpserver.SimpleFileServer.OutputLevel;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

public class SimpleWebServer {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("jep408").toRealPath(); // must be absolute
        Files.writeString(root.resolve("hello.txt"), "Hello from a temp dir\n");
        Files.writeString(root.resolve("index.html"), "<h1>It works</h1>\n");

        // Bind to loopback only, port 0 = any free port
        InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        HttpServer server = SimpleFileServer.createFileServer(address, root, OutputLevel.NONE);
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();

            show(client, HttpRequest.newBuilder(URI.create(base + "/hello.txt")).build());
            show(client, HttpRequest.newBuilder(URI.create(base + "/")).build()); // index.html
            show(client, HttpRequest.newBuilder(URI.create(base + "/missing.txt")).build());
            // Paths that try to climb out of the root are not served
            show(client, HttpRequest.newBuilder(URI.create(base + "/../../etc/passwd")).build());
            // Only GET and HEAD are supported
            show(client, HttpRequest.newBuilder(URI.create(base + "/hello.txt"))
                    .POST(HttpRequest.BodyPublishers.ofString("x")).build());
        } finally {
            server.stop(0);
            Files.delete(root.resolve("hello.txt"));
            Files.delete(root.resolve("index.html"));
            Files.delete(root);
        }
    }

    static void show(HttpClient client, HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String type = response.headers().firstValue("Content-Type").orElse("-");
        String body = response.statusCode() == 200 ? response.body().strip() : "";
        String line = String.format("%-4s %-17s -> %d %-25s %s", request.method(), request.uri().getRawPath(),
                response.statusCode(), type, body);
        System.out.println(line.stripTrailing());
    }
}

/* Output:
GET  /hello.txt        -> 200 text/plain                Hello from a temp dir
GET  /                 -> 200 text/html                 <h1>It works</h1>
GET  /missing.txt      -> 404 text/html; charset=UTF-8
GET  /../../etc/passwd -> 404 text/html; charset=UTF-8
POST /hello.txt        -> 405 -
*/
