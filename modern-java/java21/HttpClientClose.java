// Java 21 — java.net.http.HttpClient is AutoCloseable (API addition, no JEP)
//
// The Java 11 HttpClient owns a selector thread, connection pools and
// possibly an executor, but it had no close method: its resources were
// released only when the client object became unreachable and was garbage
// collected. Short-lived clients (tests, per-tenant clients, CLI tools)
// therefore leaked threads and sockets for an unpredictable time, and
// there was no way to wait for in-flight requests before exiting. Java 21
// adds shutdown() (finish in-flight requests, accept no new ones),
// shutdownNow() (abort them), awaitTermination(Duration), isTerminated(),
// and close(), which is shutdown + wait, so try-with-resources bounds the
// client's lifetime. The server here is a local HttpServer on a loopback
// ephemeral port.
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
import java.util.concurrent.CompletableFuture;

public class HttpClientClose {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/slow", exchange -> {
            sleep(300);
            byte[] body = "slow answer".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        URI slow = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/slow");
        HttpRequest request = HttpRequest.newBuilder(slow).build();

        try {
            // Before: no way to release the client; its selector thread stays until GC
            HttpClient old = HttpClient.newHttpClient();
            old.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("before: selector threads alive after use = " + selectorThreads());
            old.close(); // (only possible since Java 21; tidy up the 'before' client)

            // After: try-with-resources; close() waits for the in-flight request
            CompletableFuture<HttpResponse<String>> pending;
            long start = System.nanoTime();
            try (HttpClient client = HttpClient.newHttpClient()) {
                pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            } // close(): shutdown() + wait until terminated
            long blockedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
            // the handler sleeps 300 ms, so leaving the block that late means close() waited for it
            System.out.println("after: close() waited for the in-flight request = " + (blockedMillis >= 250)
                    + ", body = " + pending.join().body());
            System.out.println("after: selector threads alive = " + selectorThreads());

            // shutdown(): no new requests, but in-flight ones complete
            HttpClient client = HttpClient.newHttpClient();
            CompletableFuture<HttpResponse<String>> inFlight = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            client.shutdown();
            try {
                client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                System.out.println("send after shutdown(): " + e.getClass().getSimpleName());
            }
            System.out.println("in-flight request still answered: " + inFlight.join().statusCode());
            System.out.println("awaitTermination: " + client.awaitTermination(Duration.ofSeconds(5))
                    + ", isTerminated: " + client.isTerminated());

            // shutdownNow(): abort what is still running
            HttpClient impatient = HttpClient.newHttpClient();
            CompletableFuture<HttpResponse<String>> aborted = impatient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            sleep(50);
            impatient.shutdownNow();
            System.out.println("shutdownNow: request failed = "
                    + aborted.handle((r, e) -> e != null).join());
            impatient.awaitTermination(Duration.ofSeconds(5));
        } finally {
            server.stop(0);
            ((java.util.concurrent.ExecutorService) server.getExecutor()).shutdownNow();
        }
    }

    static long selectorThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().contains("SelectorManager"))
                .count();
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

/* Output:
before: selector threads alive after use = 1
after: close() waited for the in-flight request = true, body = slow answer
after: selector threads alive = 0
send after shutdown(): IOException
in-flight request still answered: 200
awaitTermination: true, isTerminated: true
shutdownNow: request failed = true
*/
