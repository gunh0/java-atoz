// Misuse: the "trust all certificates" TrustManager
//
// When a test server has a self-signed certificate, the top search result
// for "PKIX path building failed" is an X509TrustManager whose check
// methods are empty, often paired with a HostnameVerifier that returns true.
// It makes the error go away by switching TLS authentication off: the
// connection is still encrypted, but to whoever answers, so any man in the
// middle with any certificate reads and modifies the traffic. Such code
// tends to survive into production (it has been found in many mobile apps
// and SDKs). The fix is to trust the test CA explicitly: put its
// certificate in a trust store for the test environment and keep PKIX
// validation and hostname verification on, as below. Everything runs over
// loopback with throwaway test certificates.
import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class TrustAllCertificates {
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
    static final String EVIL_CERT = """
            -----BEGIN CERTIFICATE-----
            MIIBfzCCASWgAwIBAgIIMnQC2Mk5KtwwCgYIKoZIzj0EAwIwJjEQMA4GA1UEChMH
            TWFsbG9yeTESMBAGA1UEAxMJbG9jYWxob3N0MB4XDTI0MDEwMTAwMDAwMFoXDTQw
            MDYwNTAwMDAwMFowJjEQMA4GA1UEChMHTWFsbG9yeTESMBAGA1UEAxMJbG9jYWxo
            b3N0MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE7RdqkgUdqIwIgLASyeQYUB2b
            8VxQfZWcrBvVDHFtQm0KMsbiYJF6WKGZW92fAmuIkMkk1qc9ui0qj3TtJRTSfqM9
            MDswHQYDVR0OBBYEFGdStSS0WliIPsrTJQynB+Wd0a1gMBoGA1UdEQQTMBGCCWxv
            Y2FsaG9zdIcEfwAAATAKBggqhkjOPQQDAgNIADBFAiEA8J2gIbpHl8K4Qho9h0Tf
            UQu8J1Vp4HQ43wC7S0rm9KICIEAuo7LdCPFibtMO8+ZYh3LTXvYL3eYQxHfF0ddl
            vfYl
            -----END CERTIFICATE-----
            """;
    static final String EVIL_KEY = """
            -----BEGIN PRIVATE KEY-----
            MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCAXH/wPv/+/m9pR1VU6
            KqMAGXRdATsx9cPKp5Dz6P0S3A==
            -----END PRIVATE KEY-----
            """;

    // Noncompliant: accepts every certificate chain
    static final X509TrustManager TRUST_ALL = new X509TrustManager() {
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { }
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    };

    public static void main(String[] args) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        SSLContext realServer = server(LEAF_KEY, cert(cf, LEAF_CERT), cert(cf, INTERMEDIATE_CA));
        SSLContext attacker = server(EVIL_KEY, cert(cf, EVIL_CERT)); // self-signed "CN=localhost, O=Mallory"

        SSLContext trustAll = SSLContext.getInstance("TLS");
        trustAll.init(null, new TrustManager[] {TRUST_ALL}, null);

        // Compliant: trust exactly the (test) CA, keep all checks on
        KeyStore ts = KeyStore.getInstance("PKCS12");
        ts.load(null, null);
        ts.setCertificateEntry("test-root", cert(cf, ROOT_CA));
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(ts);
        SSLContext testCaTrust = SSLContext.getInstance("TLS");
        testCaTrust.init(null, tmf.getTrustManagers(), null);

        System.out.println("noncompliant (trust all), real server: " + connect(realServer, trustAll));
        System.out.println("noncompliant (trust all), attacker:    " + connect(attacker, trustAll));
        System.out.println("compliant (test CA),      real server: " + connect(realServer, testCaTrust));
        System.out.println("compliant (test CA),      attacker:    " + connect(attacker, testCaTrust));
    }

    static String connect(SSLContext serverCtx, SSLContext clientCtx) throws Exception {
        try (SSLServerSocket server = (SSLServerSocket) serverCtx.getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            CompletableFuture<Void> serverSide = CompletableFuture.runAsync(() -> {
                try (SSLSocket s = (SSLSocket) server.accept()) {
                    s.setSoTimeout(5000);
                    s.startHandshake();
                } catch (Exception ignored) {
                    // the compliant client hangs up on the attacker
                }
            });
            try (SSLSocket client = (SSLSocket) clientCtx.getSocketFactory().createSocket("localhost", server.getLocalPort())) {
                client.setSoTimeout(5000);
                SSLParameters p = client.getSSLParameters();
                p.setEndpointIdentificationAlgorithm("HTTPS");
                client.setSSLParameters(p);
                client.startHandshake();
                return "connected to " + client.getSession().getPeerPrincipal().getName();
            } catch (SSLException e) {
                return "rejected: " + e.getClass().getSimpleName();
            } finally {
                serverSide.get(5, TimeUnit.SECONDS);
            }
        }
    }

    static SSLContext server(String keyPem, Certificate... chain) throws Exception {
        char[] pw = "in-memory".toCharArray();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("server", KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(
                Base64.getMimeDecoder().decode(keyPem.replaceAll("-----[A-Z ]+-----", "")))), pw, chain);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
        kmf.init(ks, pw);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    static Certificate cert(CertificateFactory cf, String pem) throws Exception {
        return cf.generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
    }
}

/* Output:
noncompliant (trust all), real server: connected to CN=localhost,O=java-atoz
noncompliant (trust all), attacker:    connected to CN=localhost,O=Mallory
compliant (test CA),      real server: connected to CN=localhost,O=java-atoz
compliant (test CA),      attacker:    rejected: SSLHandshakeException
*/
