// TLS from scratch in one JVM: SSLContext, key/trust managers, a handshake
//
// JSSE's moving parts: a KeyStore with the server's private key and chain
// feeds a KeyManagerFactory ("who am I"), a KeyStore with trusted CA
// certificates feeds a TrustManagerFactory ("whom do I believe"), and
// SSLContext.init(keyManagers, trustManagers, random) combines them into a
// factory for SSLSocket/SSLServerSocket/SSLEngine. Both stores here live only
// in memory, built from a throwaway test PKI (root -> intermediate -> leaf
// "localhost"). The client enables endpoint identification ("HTTPS"), which
// SSLSocket does not do by default. After the handshake, the SSLSession
// tells you what was negotiated; TLS 1.3 offers only AEAD cipher suites
// (AES-GCM, ChaCha20-Poly1305). Everything runs over the loopback interface.
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

public class LoopbackHandshake {
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

        // Server identity: private key + chain [leaf, intermediate]
        KeyStore serverStore = KeyStore.getInstance("PKCS12");
        serverStore.load(null, null);
        serverStore.setKeyEntry("server", KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der(LEAF_KEY))),
                pw, new Certificate[] {cert(cf, LEAF_CERT), cert(cf, INTERMEDIATE_CA)});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
        kmf.init(serverStore, pw);
        SSLContext serverCtx = SSLContext.getInstance("TLS");
        serverCtx.init(kmf.getKeyManagers(), null, null);

        // Client trust: only the test root
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("test-root", cert(cf, ROOT_CA));
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(trustStore);
        SSLContext clientCtx = SSLContext.getInstance("TLS");
        clientCtx.init(null, tmf.getTrustManagers(), null);

        try (SSLServerSocket server = (SSLServerSocket) serverCtx.getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            CompletableFuture<String> serverSide = CompletableFuture.supplyAsync(() -> {
                try (SSLSocket s = (SSLSocket) server.accept()) {
                    s.setSoTimeout(5000);
                    BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    String line = in.readLine();
                    new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8).println("hello, " + line);
                    return "server read \"" + line + "\" over " + s.getSession().getProtocol();
                } catch (Exception e) {
                    return "server failed: " + e.getClass().getSimpleName();
                }
            });

            try (SSLSocket client = (SSLSocket) clientCtx.getSocketFactory().createSocket("localhost", server.getLocalPort())) {
                client.setSoTimeout(5000);
                SSLParameters p = client.getSSLParameters();
                p.setEndpointIdentificationAlgorithm("HTTPS"); // check the name in the certificate
                client.setSSLParameters(p);
                client.startHandshake();

                SSLSession session = client.getSession();
                String suite = session.getCipherSuite();
                System.out.println("protocol:     " + session.getProtocol());
                String family = suite.contains("_GCM_") ? "AES-GCM" : suite.contains("CHACHA20") ? "ChaCha20-Poly1305" : suite;
                System.out.println("cipher suite: " + family + " (exact suite depends on both sides' preferences)");
                System.out.println("server:       " + session.getPeerPrincipal().getName());
                System.out.println("chain sent:   " + session.getPeerCertificates().length + " certificates (leaf + intermediate)");
                System.out.println("client cert:  " + (session.getLocalCertificates() == null ? "none" : "sent"));

                new PrintWriter(client.getOutputStream(), true, StandardCharsets.UTF_8).println("client");
                String reply = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8)).readLine();
                System.out.println("client got:   " + reply);
            }
            System.out.println(serverSide.get(5, TimeUnit.SECONDS));
        }
    }

    static Certificate cert(CertificateFactory cf, String pem) throws Exception {
        return cf.generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
    }

    static byte[] der(String pem) {
        return Base64.getMimeDecoder().decode(pem.replaceAll("-----[A-Z ]+-----", ""));
    }
}

/* Output:
protocol:     TLSv1.3
cipher suite: AES-GCM (exact suite depends on both sides' preferences)
server:       CN=localhost,O=java-atoz
chain sent:   2 certificates (leaf + intermediate)
client cert:  none
client got:   hello, client
server read "client" over TLSv1.3
*/
