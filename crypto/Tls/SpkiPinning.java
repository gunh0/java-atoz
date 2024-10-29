// Certificate pinning by SPKI hash, as an extra check after PKIX validation
//
// PKIX trusts every CA in the trust store, so one compromised or coerced CA,
// or a corporate interception root installed on the device, can issue a
// valid certificate for your server. A client that talks only to its own
// backend can additionally pin the expected public key: hash the
// certificate's SubjectPublicKeyInfo (SHA-256, Base64, as in HPKP/RFC 7469
// and OkHttp) and require that some certificate in the validated chain
// matches. Pinning the key, not the certificate, survives certificate
// renewal with the same key; always ship a backup pin (e.g. the
// intermediate CA or a spare key) or a key rotation locks clients out.
// Here the client trusts two roots, one of which "Mallory" controls.
import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

public class SpkiPinning {
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

    public static void main(String[] args) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate leaf = cert(cf, LEAF_CERT), inter = cert(cf, INTERMEDIATE_CA);
        X509Certificate root = cert(cf, ROOT_CA), evil = cert(cf, EVIL_CERT);

        String leafPin = pin(leaf), interPin = pin(inter);
        System.out.println("pin (leaf key):         sha256/" + leafPin);
        System.out.println("backup pin (intermed.): sha256/" + interPin);

        SSLContext realServer = server(LEAF_KEY, leaf, inter);
        SSLContext mitmServer = server(EVIL_KEY, evil);

        // The client's trust store holds the real root AND Mallory's root
        KeyStore ts = KeyStore.getInstance("PKCS12");
        ts.load(null, null);
        ts.setCertificateEntry("test-root", root);
        ts.setCertificateEntry("installed-by-mallory", evil);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(ts);
        X509ExtendedTrustManager pkix = (X509ExtendedTrustManager) tmf.getTrustManagers()[0];

        SSLContext plainClient = SSLContext.getInstance("TLS");
        plainClient.init(null, new TrustManager[] {pkix}, null);
        SSLContext pinnedClient = SSLContext.getInstance("TLS");
        pinnedClient.init(null, new TrustManager[] {new PinningTrustManager(pkix, Set.of(leafPin, interPin))}, null);

        System.out.println();
        System.out.println("PKIX only, real server:         " + connect(realServer, plainClient));
        System.out.println("PKIX only, interception server: " + connect(mitmServer, plainClient));
        System.out.println("pinned,    real server:         " + connect(realServer, pinnedClient));
        System.out.println("pinned,    interception server: " + connect(mitmServer, pinnedClient));
    }

    static String pin(X509Certificate c) throws Exception {
        return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(c.getPublicKey().getEncoded()));
    }

    // Runs the normal checks first, then requires a pinned key in the chain.
    static final class PinningTrustManager extends X509ExtendedTrustManager {
        private final X509ExtendedTrustManager delegate;
        private final Set<String> pins;

        PinningTrustManager(X509ExtendedTrustManager delegate, Set<String> pins) {
            this.delegate = delegate;
            this.pins = pins;
        }

        private void checkPins(X509Certificate[] chain) throws CertificateException {
            for (X509Certificate c : chain) {
                try {
                    if (pins.contains(pin(c))) return;
                } catch (Exception e) {
                    throw new CertificateException(e);
                }
            }
            throw new CertificateException("no pinned public key in the server's chain");
        }

        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            delegate.checkServerTrusted(chain, authType, socket);
            checkPins(chain);
        }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            delegate.checkServerTrusted(chain, authType, engine);
            checkPins(chain);
        }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkServerTrusted(chain, authType);
            checkPins(chain);
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
        @Override public X509Certificate[] getAcceptedIssuers() {
            return delegate.getAcceptedIssuers();
        }
    }

    static String connect(SSLContext serverCtx, SSLContext clientCtx) throws Exception {
        try (SSLServerSocket server = (SSLServerSocket) serverCtx.getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            CompletableFuture<Void> serverSide = CompletableFuture.runAsync(() -> {
                try (SSLSocket s = (SSLSocket) server.accept()) {
                    s.setSoTimeout(5000);
                    s.startHandshake();
                } catch (Exception ignored) {
                    // rejected by the client
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

    static SSLContext server(String keyPem, X509Certificate... chain) throws Exception {
        char[] pw = "in-memory".toCharArray();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("server", KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der(keyPem))), pw, chain);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
        kmf.init(ks, pw);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    static X509Certificate cert(CertificateFactory cf, String pem) throws Exception {
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
    }

    static byte[] der(String pem) {
        return Base64.getMimeDecoder().decode(pem.replaceAll("-----[A-Z ]+-----", ""));
    }
}

/* Output:
pin (leaf key):         sha256/bzTgENWqhL73LDxcbnCnvNM3IVQQ51K8D3YFKZxSr+c=
backup pin (intermed.): sha256/lBf2Z4kePeIqSbA6v1DvMaR8Q4zLdqZTkV6RpH+i8jc=

PKIX only, real server:         connected to CN=localhost,O=java-atoz
PKIX only, interception server: connected to CN=localhost,O=Mallory
pinned,    real server:         connected to CN=localhost,O=java-atoz
pinned,    interception server: rejected: SSLHandshakeException
*/
