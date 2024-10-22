// TLS protocol versions: what is enabled, what gets negotiated, what is refused
//
// SSL 3.0, TLS 1.0 and TLS 1.1 are deprecated (RFC 8996): POODLE, BEAST,
// weak MACs and SHA-1 handshakes. The JDK keeps them in its code but lists
// them in the jdk.tls.disabledAlgorithms security property, so even code
// that explicitly enables them cannot negotiate them. A handshake settles on
// the highest version both sides enable; TLS 1.3 removed RSA key transport,
// CBC suites and renegotiation, and encrypts most of the handshake. Restrict
// with SSLParameters.setProtocols (or jdk.tls.client.protocols /
// jdk.tls.server.protocols), not by re-enabling legacy versions "for
// compatibility". This program tries several client/server combinations
// over loopback with the test PKI.
import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

public class ProtocolVersions {
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

    static SSLContext serverCtx, clientCtx;

    public static void main(String[] args) throws Exception {
        setUp();
        List<String> enabled = Arrays.asList(clientCtx.getDefaultSSLParameters().getProtocols());
        System.out.println("enabled by default: " + enabled);
        System.out.println("TLSv1.1 / TLSv1 enabled: " + (enabled.contains("TLSv1.1") || enabled.contains("TLSv1")));
        System.out.println();

        String[] all = {"TLSv1.3", "TLSv1.2"};
        attempt("client 1.3+1.2, server 1.3+1.2", all, all);
        attempt("client 1.2 only, server 1.3+1.2", new String[] {"TLSv1.2"}, all);
        attempt("client 1.3+1.2, server 1.2 only", all, new String[] {"TLSv1.2"});
        attempt("client 1.2 only, server 1.3 only", new String[] {"TLSv1.2"}, new String[] {"TLSv1.3"});
        // Explicitly enabling a disabled version does not bring it back
        attempt("client 1.1 only, server 1.3+1.2", new String[] {"TLSv1.1"}, all);
    }

    static void attempt(String label, String[] clientProtocols, String[] serverProtocols) throws Exception {
        try (SSLServerSocket server = (SSLServerSocket) serverCtx.getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setEnabledProtocols(serverProtocols);
            CompletableFuture<Void> serverSide = CompletableFuture.runAsync(() -> {
                try (SSLSocket s = (SSLSocket) server.accept()) {
                    s.setSoTimeout(5000);
                    s.startHandshake();
                } catch (Exception ignored) {
                    // failures are reported from the client side
                }
            });
            String result;
            try (SSLSocket client = (SSLSocket) clientCtx.getSocketFactory().createSocket("localhost", server.getLocalPort())) {
                client.setSoTimeout(5000);
                client.setEnabledProtocols(clientProtocols);
                client.startHandshake();
                String suite = client.getSession().getCipherSuite();
                result = client.getSession().getProtocol() + (suite.startsWith("TLS_ECDHE_ECDSA_") ? " (ECDHE-ECDSA suite)" : "");
            } catch (SSLException e) {
                result = "failed: " + e.getClass().getSimpleName();
            }
            serverSide.get(5, TimeUnit.SECONDS);
            System.out.printf("%-34s -> %s%n", label, result);
        }
    }

    static void setUp() throws Exception {
        char[] pw = "in-memory".toCharArray();
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("server", KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der(LEAF_KEY))),
                pw, new Certificate[] {cert(cf, LEAF_CERT), cert(cf, INTERMEDIATE_CA)});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
        kmf.init(ks, pw);
        serverCtx = SSLContext.getInstance("TLS");
        serverCtx.init(kmf.getKeyManagers(), null, null);

        KeyStore ts = KeyStore.getInstance("PKCS12");
        ts.load(null, null);
        ts.setCertificateEntry("test-root", cert(cf, ROOT_CA));
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(ts);
        clientCtx = SSLContext.getInstance("TLS");
        clientCtx.init(null, tmf.getTrustManagers(), null);
    }

    static Certificate cert(CertificateFactory cf, String pem) throws Exception {
        return cf.generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
    }

    static byte[] der(String pem) {
        return Base64.getMimeDecoder().decode(pem.replaceAll("-----[A-Z ]+-----", ""));
    }
}

/* Output:
enabled by default: [TLSv1.3, TLSv1.2]
TLSv1.1 / TLSv1 enabled: false

client 1.3+1.2, server 1.3+1.2     -> TLSv1.3
client 1.2 only, server 1.3+1.2    -> TLSv1.2 (ECDHE-ECDSA suite)
client 1.3+1.2, server 1.2 only    -> TLSv1.2 (ECDHE-ECDSA suite)
client 1.2 only, server 1.3 only   -> failed: SSLHandshakeException
client 1.1 only, server 1.3+1.2    -> failed: SSLHandshakeException
*/
