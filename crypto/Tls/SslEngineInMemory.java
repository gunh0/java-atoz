// SSLEngine: a TLS handshake between two engines, in memory, no sockets
//
// SSLSocket hides TLS behind a blocking stream. SSLEngine is the transport-
// independent core: you hand it bytes and it hands bytes back, which is what
// NIO servers, Netty and HTTP/2 stacks build on. Its contract is a state
// machine: wrap() turns application data (or nothing, during the handshake)
// into TLS records for the network; unwrap() consumes records from the
// network; getHandshakeStatus() says what it needs next (NEED_WRAP,
// NEED_UNWRAP, NEED_TASK for delegated work such as certificate checks, then
// FINISHED / NOT_HANDSHAKING). Here the "network" is two ByteBuffers, so the
// whole handshake, the data exchange, an attack and the close_notify
// exchange happen in one thread, deterministically. The test PKI is the
// same throwaway one as in LoopbackHandshake.java.
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

public class SslEngineInMemory {
    static final String ROOT_CA = """
            -----BEGIN CERTIFICATE-----
            MIIBozCCAUmgAwIBAgIJAI/ZH5K+jzLyMAoGCCqGSM49BAMCMDUxEjAQBgNVBAoT
            CWphdmEtYXRvejEfMB0GA1UEAxMWamF2YS1hdG96IFRlc3QgUm9vdCBDQTAeFw0y
            NDAxMDEwMDAwMDBaFw00MzEyMjcwMDAwMDBaMDUxEjAQBgNVBAoTCWphdmEtYXRv
            ejEfMB0GA1UEAxMWamF2YS1hdG96IFRlc3QgUm9vdCBDQTBZMBMGByqGSM49AgEG
            CCqGSM49AwEHA0IABEzsT1MqDTVbrmZXwa86wAwgOEp0KHP1QUtjRzDFduBHh5du
            aoF6jJiK9nlGK3gOMsYBWGqcvwrgdt8+1UtBjtmjQjBAMB0GA1UdDgQWBBRJomL2
            hdtfM9PmUl1YmYuKJRXoXTAOBgNVHQ8BAf8EBAMCAQYwDwYDVR0TAQH/BAUwAwEB
            /zAKBggqhkjOPQQDAgNIADBFAiEAoTfAH6jfzaN/Z5zKRmdLVT5DNcS4vCBSKrzF
            /w7ctHACIB5Boh3Z6YqNd8MzC5vQgLv/cD42erdr+iJsw3IIE/Tn
            -----END CERTIFICATE-----
            """;
    static final String INTERMEDIATE_CA = """
            -----BEGIN CERTIFICATE-----
            MIIBzjCCAXSgAwIBAgIIHrf1pCDCoOwwCgYIKoZIzj0EAwIwNTESMBAGA1UEChMJ
            amF2YS1hdG96MR8wHQYDVQQDExZqYXZhLWF0b3ogVGVzdCBSb290IENBMB4XDTI0
            MDEwMTAwMDAwMFoXDTQzMDMwMjAwMDAwMFowPTESMBAGA1UEChMJamF2YS1hdG96
            MScwJQYDVQQDEx5qYXZhLWF0b3ogVGVzdCBJbnRlcm1lZGlhdGUgQ0EwWTATBgcq
            hkjOPQIBBggqhkjOPQMBBwNCAAQOdpl+Xx7Ce7ud5QGvDhGj+JsnTZW/6cxx90gs
            1I4C5rhjI3nOM4bLjAP7LgQxur1ubTnTFJrITSej46RsuYoTo2YwZDAdBgNVHQ4E
            FgQUjPWG6Du55oIX3pPfnYJHEBAbMDIwDgYDVR0PAQH/BAQDAgEGMBIGA1UdEwEB
            /wQIMAYBAf8CAQAwHwYDVR0jBBgwFoAUSaJi9oXbXzPT5lJdWJmLiiUV6F0wCgYI
            KoZIzj0EAwIDSAAwRQIgH12X8neIg+/TBfjALMVBvDPZgc95L4xA0PiUcMcYnHAC
            IQCM2llzUzd3tBw5lp5pLW0Pb0iPk22RekrgcyJmTpZQhA==
            -----END CERTIFICATE-----
            """;
    static final String LEAF_CERT = """
            -----BEGIN CERTIFICATE-----
            MIIB4DCCAYagAwIBAgIIS6MTeyhjZzcwCgYIKoZIzj0EAwIwPTESMBAGA1UEChMJ
            amF2YS1hdG96MScwJQYDVQQDEx5qYXZhLWF0b3ogVGVzdCBJbnRlcm1lZGlhdGUg
            Q0EwHhcNMjQwMTAxMDAwMDAwWhcNNDAwNjA1MDAwMDAwWjAoMRIwEAYDVQQKEwlq
            YXZhLWF0b3oxEjAQBgNVBAMTCWxvY2FsaG9zdDBZMBMGByqGSM49AgEGCCqGSM49
            AwEHA0IABM1mBlSh40VsCLWA3CZgaDt6cdgvV88QdgoiLXKCVdfyuseDe0B70Ytw
            +nnx4L4eUW2tnrLHm3f1/R4apmBN/GqjgYQwgYEwHQYDVR0OBBYEFA/oNdqMpHqK
            +P4fj9RwT8bSO6VtMA4GA1UdDwEB/wQEAwIHgDAaBgNVHREEEzARgglsb2NhbGhv
            c3SHBH8AAAEwHwYDVR0jBBgwFoAUjPWG6Du55oIX3pPfnYJHEBAbMDIwEwYDVR0l
            BAwwCgYIKwYBBQUHAwEwCgYIKoZIzj0EAwIDSAAwRQIgJF0WuzVYvPQAztmydI7w
            n8BrmSA8pC/gM9JznZJWyRICIQCDwB6lWsSsGovuFLyqH5lLSXyKj9K8OlGv8VVO
            Xvxpog==
            -----END CERTIFICATE-----
            """;
    static final String LEAF_KEY = """
            -----BEGIN PRIVATE KEY-----
            MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCDUEvE3L4Vzpvvk9QxB
            lQORDyrmX8ocaCDT81k611fSww==
            -----END PRIVATE KEY-----
            """;

    public static void main(String[] args) throws Exception {
        SSLContext[] ctx = contexts();
        SSLEngine client = ctx[1].createSSLEngine("localhost", 443); // host/port: for SNI and session reuse
        client.setUseClientMode(true);
        SSLParameters p = client.getSSLParameters();
        p.setEndpointIdentificationAlgorithm("HTTPS");
        client.setSSLParameters(p);
        SSLEngine server = ctx[0].createSSLEngine();
        server.setUseClientMode(false);

        // The in-memory network: what each side has sent and the peer has not read yet
        int packet = client.getSession().getPacketBufferSize();
        ByteBuffer clientToServer = ByteBuffer.allocate(packet * 4);
        ByteBuffer serverToClient = ByteBuffer.allocate(packet * 4);
        ByteBuffer clientApp = ByteBuffer.allocate(client.getSession().getApplicationBufferSize());
        ByteBuffer serverApp = ByteBuffer.allocate(server.getSession().getApplicationBufferSize());
        ByteBuffer nothing = ByteBuffer.allocate(0);

        client.beginHandshake();
        server.beginHandshake();
        for (int round = 0; round < 20 && !(idle(client) && idle(server)); round++) {
            client.wrap(nothing, clientToServer);
            runTasks(client);
            server.wrap(nothing, serverToClient);
            runTasks(server);
            clientToServer.flip();
            serverToClient.flip();
            client.unwrap(serverToClient, clientApp);
            runTasks(client);
            server.unwrap(clientToServer, serverApp);
            runTasks(server);
            clientToServer.compact();
            serverToClient.compact();
        }
        System.out.println("handshake finished:  " + (idle(client) && idle(server)));
        System.out.println("delegated tasks run: " + (tasks > 0) + " (certificate checks, key exchange)");
        System.out.println("protocol:            " + client.getSession().getProtocol());
        System.out.println("server certificate:  " + client.getSession().getPeerPrincipal().getName());

        // Application data, client -> server: one record, encrypted and authenticated
        clientToServer.clear();
        client.wrap(ByteBuffer.wrap("GET / HTTP/1.1".getBytes(StandardCharsets.UTF_8)), clientToServer);
        clientToServer.flip();
        int recordBytes = clientToServer.remaining();
        serverApp.clear();
        server.unwrap(clientToServer, serverApp);
        serverApp.flip();
        System.out.println("server received:     \"" + StandardCharsets.UTF_8.decode(serverApp) + "\" (14 bytes became one "
                + recordBytes + "-byte record: header + ciphertext + AEAD tag)");

        // An attacker flips one bit of the next record in transit
        clientToServer.clear();
        client.wrap(ByteBuffer.wrap("transfer 10 EUR".getBytes(StandardCharsets.UTF_8)), clientToServer);
        clientToServer.flip();
        int last = clientToServer.limit() - 1;
        clientToServer.put(last, (byte) (clientToServer.get(last) ^ 1));
        try {
            serverApp.clear();
            server.unwrap(clientToServer, serverApp);
            System.out.println("tampered record accepted?!");
        } catch (SSLException e) {
            System.out.println("tampered record:     rejected with SSLException; server inbound closed: " + server.isInboundDone());
        }

        // Orderly shutdown on the client side: close_notify goes out
        client.closeOutbound();
        clientToServer.clear();
        SSLEngineResult r = client.wrap(nothing, clientToServer);
        System.out.println("client close:        " + r.getStatus() + ", close_notify bytes: " + (r.bytesProduced() > 0)
                + ", outbound done: " + client.isOutboundDone());
    }

    static int tasks;

    static boolean idle(SSLEngine e) {
        return e.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING;
    }

    static void runTasks(SSLEngine e) {
        Runnable task;
        while ((task = e.getDelegatedTask()) != null) {
            task.run(); // may block or be slow: real servers run these on a worker pool
            tasks++;
        }
    }

    static SSLContext[] contexts() throws Exception {
        char[] pw = "in-memory".toCharArray();
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        KeyStore serverStore = KeyStore.getInstance("PKCS12");
        serverStore.load(null, null);
        serverStore.setKeyEntry("server", KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der(LEAF_KEY))),
                pw, new Certificate[] {cert(cf, LEAF_CERT), cert(cf, INTERMEDIATE_CA)});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
        kmf.init(serverStore, pw);
        SSLContext serverCtx = SSLContext.getInstance("TLS");
        serverCtx.init(kmf.getKeyManagers(), null, null);

        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("test-root", cert(cf, ROOT_CA));
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(trustStore);
        SSLContext clientCtx = SSLContext.getInstance("TLS");
        clientCtx.init(null, tmf.getTrustManagers(), null);
        return new SSLContext[] {serverCtx, clientCtx};
    }

    static Certificate cert(CertificateFactory cf, String pem) throws Exception {
        return cf.generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
    }

    static byte[] der(String pem) {
        return Base64.getMimeDecoder().decode(pem.replaceAll("-----[A-Z ]+-----", ""));
    }
}

/* Output:
handshake finished:  true
delegated tasks run: true (certificate checks, key exchange)
protocol:            TLSv1.3
server certificate:  CN=localhost,O=java-atoz
server received:     "GET / HTTP/1.1" (14 bytes became one 52-byte record: header + ciphertext + AEAD tag)
tampered record:     rejected with SSLException; server inbound closed: true
client close:        CLOSED, close_notify bytes: true, outbound done: true
*/
