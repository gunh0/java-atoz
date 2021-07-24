// JEP 321 — HTTP Client (Standard) (Java 11)
//
// HttpURLConnection dates from JDK 1.1: blocking only, HTTP/1.1 only, mutable
// and awkward to use. java.net.http.HttpClient replaces it with an immutable,
// builder-based API that speaks HTTP/1.1 and HTTP/2, supports both
// synchronous send() and asynchronous sendAsync() (CompletableFuture), and
// converts bodies with BodyPublishers / BodyHandlers.
// To stay offline, this example talks to a local server bound to an
// ephemeral loopback port.
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class HttpClientExample {
    public static void main(String[] args) throws Exception {
        HttpServer server = startServer();
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            // Synchronous GET
            HttpRequest get = HttpRequest.newBuilder(base.resolve("/hello?name=java")).GET().build();
            HttpResponse<String> response = client.send(get, HttpResponse.BodyHandlers.ofString());
            System.out.println("GET  " + response.statusCode() + " " + response.body());
            System.out.println("content-type: " + response.headers().firstValue("content-type").orElse("?"));

            // POST with a string body
            HttpRequest post = HttpRequest.newBuilder(base.resolve("/echo"))
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString("ping"))
                    .build();
            System.out.println("POST " + client.send(post, HttpResponse.BodyHandlers.ofString()).body());

            // Asynchronous: returns at once, completes later
            CompletableFuture<Integer> length = client
                    .sendAsync(HttpRequest.newBuilder(base.resolve("/missing")).build(),
                            HttpResponse.BodyHandlers.ofString())
                    .thenApply(r -> {
                        System.out.println("async status: " + r.statusCode());
                        return r.body().length();
                    });
            System.out.println("async body length: " + length.join());
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer startServer() throws IOException {
        // Port 0 = pick any free port; loopback only
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/hello", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String name = query == null ? "world" : query.substring(query.indexOf('=') + 1);
            reply(exchange, 200, "Hello, " + name + "!");
        });
        server.createContext("/echo", exchange -> {
            try (InputStream in = exchange.getRequestBody()) {
                String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                reply(exchange, 200, exchange.getRequestMethod() + " body=" + body);
            }
        });
        server.createContext("/", exchange -> reply(exchange, 404, "not found"));
        server.start();
        return server;
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}

/* Output:
GET  200 Hello, java!
content-type: text/plain; charset=utf-8
POST POST body=ping
async status: 404
async body length: 9
*/
