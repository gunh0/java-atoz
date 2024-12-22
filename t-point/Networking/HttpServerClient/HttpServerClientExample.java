// A REST round trip inside one JVM: the JDK's built-in
// com.sun.net.httpserver.HttpServer serves a tiny in-memory "notes" API on
// the loopback interface, and java.net.http.HttpClient (Java 11) calls it.
//  - binding to 127.0.0.1 port 0 lets the OS pick a free port and keeps the
//    server unreachable from other machines
//  - a handler must always send headers (sendResponseHeaders) and close the
//    exchange, even for errors; -1 as length means "no body"
//  - HttpClient is immutable and reusable; send() is blocking, sendAsync()
//    returns a CompletableFuture; BodyPublishers/BodyHandlers convert bodies
// HttpServer is fine for tests and tools, not for production services.
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class HttpServerClientExample {
    static final Map<Integer, String> NOTES = new ConcurrentSkipListMap<>();
    static final AtomicInteger IDS = new AtomicInteger();

    static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    static String json(int id, String text) {
        return "{\"id\":" + id + ",\"text\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    static void handleNotes(HttpExchange ex) throws IOException {
        try (ex) {
            String path = ex.getRequestURI().getPath();          // /notes or /notes/{id}
            String rest = path.substring("/notes".length());
            switch (ex.getRequestMethod()) {
                case "GET" -> {
                    if (rest.isEmpty() || rest.equals("/")) {
                        send(ex, 200, NOTES.entrySet().stream().map(e -> json(e.getKey(), e.getValue()))
                                .collect(Collectors.joining(",", "[", "]")));
                        return;
                    }
                    String text = null;
                    try {
                        text = NOTES.get(Integer.parseInt(rest.substring(1)));
                    } catch (NumberFormatException ignored) { }
                    if (text == null) send(ex, 404, "{\"error\":\"not found\"}");
                    else send(ex, 200, json(Integer.parseInt(rest.substring(1)), text));
                }
                case "POST" -> {
                    String text = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).strip();
                    if (text.isEmpty()) {
                        send(ex, 400, "{\"error\":\"empty note\"}");
                        return;
                    }
                    int id = IDS.incrementAndGet();
                    NOTES.put(id, text);
                    ex.getResponseHeaders().set("Location", "/notes/" + id);
                    send(ex, 201, json(id, text));
                }
                case "DELETE" -> {
                    String removed = rest.length() > 1 ? NOTES.remove(Integer.parseInt(rest.substring(1))) : null;
                    send(ex, removed == null ? 404 : 204, "");
                }
                default -> {
                    ex.getResponseHeaders().set("Allow", "GET, POST, DELETE");
                    send(ex, 405, "{\"error\":\"method not allowed\"}");
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/notes", HttpServerClientExample::handleNotes);
        server.start();
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/notes");

        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            HttpResponse.BodyHandler<String> asString = HttpResponse.BodyHandlers.ofString();

            for (String text : new String[] {"rotate TLS certs", "review \"admin\" grants"}) {
                HttpRequest post = HttpRequest.newBuilder(base)
                        .header("Content-Type", "text/plain")
                        .POST(HttpRequest.BodyPublishers.ofString(text))
                        .build();
                HttpResponse<String> r = client.send(post, asString);
                System.out.println("POST   -> " + r.statusCode() + " " + r.body()
                        + " Location: " + r.headers().firstValue("location").orElse("-"));
            }

            HttpResponse<String> list = client.send(HttpRequest.newBuilder(base).GET().build(), asString);
            System.out.println("GET    -> " + list.statusCode() + " " + list.body());
            System.out.println("         content-type: " + list.headers().firstValue("content-type").orElse("-"));

            HttpResponse<String> one = client.send(HttpRequest.newBuilder(base.resolve("notes/2")).build(), asString);
            System.out.println("GET 2  -> " + one.statusCode() + " " + one.body());

            HttpResponse<String> del = client.send(
                    HttpRequest.newBuilder(base.resolve("notes/1")).DELETE().build(), asString);
            System.out.println("DEL 1  -> " + del.statusCode() + " body empty: " + del.body().isEmpty());

            HttpResponse<String> gone = client.send(HttpRequest.newBuilder(base.resolve("notes/1")).build(), asString);
            System.out.println("GET 1  -> " + gone.statusCode() + " " + gone.body());

            HttpResponse<String> empty = client.send(HttpRequest.newBuilder(base)
                    .POST(HttpRequest.BodyPublishers.ofString("   ")).build(), asString);
            System.out.println("POST \"\" -> " + empty.statusCode() + " " + empty.body());

            HttpResponse<String> put = client.send(HttpRequest.newBuilder(base)
                    .PUT(HttpRequest.BodyPublishers.noBody()).build(), asString);
            System.out.println("PUT    -> " + put.statusCode() + " Allow: " + put.headers().firstValue("allow").orElse("-"));

            // Asynchronous call
            String async = client.sendAsync(HttpRequest.newBuilder(base).build(), asString)
                    .thenApply(r -> r.statusCode() + " " + r.body())
                    .get();
            System.out.println("async  -> " + async);
        } finally {
            server.stop(0);
        }
    }
}

/* Output:
POST   -> 201 {"id":1,"text":"rotate TLS certs"} Location: /notes/1
POST   -> 201 {"id":2,"text":"review \"admin\" grants"} Location: /notes/2
GET    -> 200 [{"id":1,"text":"rotate TLS certs"},{"id":2,"text":"review \"admin\" grants"}]
         content-type: application/json; charset=utf-8
GET 2  -> 200 {"id":2,"text":"review \"admin\" grants"}
DEL 1  -> 204 body empty: true
GET 1  -> 404 {"error":"not found"}
POST "" -> 400 {"error":"empty note"}
PUT    -> 405 Allow: GET, POST, DELETE
async  -> 200 [{"id":2,"text":"review \"admin\" grants"}]
*/
