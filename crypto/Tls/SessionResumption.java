// TLS 1.3 session resumption, observed from both ends of a loopback connection
//
// A full TLS handshake costs a key exchange, a certificate chain on the wire
// and the client's path validation. After a full TLS 1.3 handshake the server
// sends NewSessionTicket messages: an opaque ticket (in the JDK, the session
// state encrypted under a server key, so the server keeps no cache) plus a
// resumption secret. The next connection to the same host and port offers
// the ticket as a pre-shared key (PSK); if the server accepts it, no
// certificate is sent or validated. The JDK uses the psk_dhe_ke mode, so a
// fresh (EC)DHE still gives forward secrecy, and it does not implement 0-RTT
// early data, which would be replayable. Security consequences to keep in
// mind: a resumed connection skips certificate checks, so a revocation or a
// trust-store change only bites at the next full handshake; ticket lifetime
// (SSLSessionContext.setSessionTimeout) and server ticket-key rotation bound
// that window; session.invalidate() forces a full handshake next time.
// Resumption is observed here by counting the client's trust-manager calls.
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

public class SessionResumption {
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
        char[] pw = "in-memory".toCharArray();
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        KeyStore serverStore = KeyStore.getInstance("PKCS12");
        serverStore.load(null, null);
        serverStore.setKeyEntry("server", KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der(LEAF_KEY))),
                pw, new Certificate[] {cert(cf, LEAF_CERT), cert(cf, INTERMEDIATE_CA)});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
        kmf.init(serverStore, pw);
        SSLContext serverCtx = SSLContext.getInstance("TLSv1.3");
        serverCtx.init(kmf.getKeyManagers(), null, null);

        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("test-root", cert(cf, ROOT_CA));
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(trustStore);
        CountingTrustManager counting = new CountingTrustManager((X509ExtendedTrustManager) tmf.getTrustManagers()[0]);
        SSLContext clientCtx = SSLContext.getInstance("TLSv1.3");
        clientCtx.init(null, new TrustManager[] {counting}, null);

        try (SSLServerSocket server = (SSLServerSocket) serverCtx.getServerSocketFactory()
                .createServerSocket(0, 10, InetAddress.getLoopbackAddress())) {
            Thread.ofPlatform().daemon().start(() -> serve(server));
            int port = server.getLocalPort();

            SSLSession first = connect(clientCtx, port, counting, "1st connection");
            SSLSession second = connect(clientCtx, port, counting, "2nd connection");
            System.out.println("same session (creation time) reused: " + (first.getCreationTime() == second.getCreationTime()));

            second.invalidate(); // e.g. after a trust-store change
            connect(clientCtx, port, counting, "after invalidate()");

            SSLContext freshCtx = SSLContext.getInstance("TLSv1.3"); // another context has its own cache
            freshCtx.init(null, new TrustManager[] {counting}, null);
            connect(freshCtx, port, counting, "new SSLContext");
        }
    }

    static SSLSession connect(SSLContext ctx, int port, CountingTrustManager tm, String label) throws Exception {
        int before = tm.calls.get();
        try (SSLSocket s = (SSLSocket) ctx.getSocketFactory().createSocket("localhost", port)) {
            s.setSoTimeout(5000);
            s.startHandshake();
            new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8).println("ping");
            // Reading application data also processes the NewSessionTicket that follows the handshake
            String reply = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)).readLine();
            boolean full = tm.calls.get() > before;
            System.out.printf("%-19s %s, %s handshake, server certificate validated: %s, reply %s%n", label + ":",
                    s.getSession().getProtocol(), full ? "full" : "resumed", full, reply);
            return s.getSession();
        }
    }

    static void serve(SSLServerSocket server) {
        while (true) {
            try (Socket raw = server.accept()) {
                SSLSocket s = (SSLSocket) raw;
                s.setSoTimeout(5000);
                String line = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)).readLine();
                new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8).println(line.replace("ping", "pong"));
            } catch (Exception e) {
                if (server.isClosed()) return;
            }
        }
    }

    // Delegates to the real PKIX trust manager and counts server-certificate checks
    static final class CountingTrustManager extends X509ExtendedTrustManager {
        final X509ExtendedTrustManager delegate;
        final AtomicInteger calls = new AtomicInteger();

        CountingTrustManager(X509ExtendedTrustManager delegate) { this.delegate = delegate; }

        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            calls.incrementAndGet();
            delegate.checkServerTrusted(chain, authType, socket);
        }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            calls.incrementAndGet();
            delegate.checkServerTrusted(chain, authType, engine);
        }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            calls.incrementAndGet();
            delegate.checkServerTrusted(chain, authType);
        }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            delegate.checkClientTrusted(chain, authType, socket);
        }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            delegate.checkClientTrusted(chain, authType, engine);
        }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkClientTrusted(chain, authType);
        }
        @Override public X509Certificate[] getAcceptedIssuers() { return delegate.getAcceptedIssuers(); }
    }

    static Certificate cert(CertificateFactory cf, String pem) throws Exception {
        return cf.generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
    }

    static byte[] der(String pem) {
        return Base64.getMimeDecoder().decode(pem.replaceAll("-----[A-Z ]+-----", ""));
    }
}

/* Output:
1st connection:     TLSv1.3, full handshake, server certificate validated: true, reply pong
2nd connection:     TLSv1.3, resumed handshake, server certificate validated: false, reply pong
same session (creation time) reused: true
after invalidate(): TLSv1.3, full handshake, server certificate validated: true, reply pong
new SSLContext:     TLSv1.3, full handshake, server certificate validated: true, reply pong
*/
