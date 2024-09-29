// KeyStore: storing keys and certificates in a PKCS#12 file
//
// A java.security.KeyStore holds three kinds of entries under aliases:
// PrivateKeyEntry (a private key plus its certificate chain: what a TLS
// server or code signer needs), SecretKeyEntry (a symmetric key) and
// TrustedCertificateEntry (a CA you trust: what a client trust store holds).
// PKCS#12 (.p12/.pfx) is the default type since Java 9 and is readable by
// OpenSSL and browsers; the old proprietary JKS should be migrated. The store
// password protects the file's integrity (a MAC); each key entry is
// encrypted under its own password. Passwords are char[] so they can be
// wiped after use. This program writes a store into a temporary directory,
// reloads it, and deletes it.
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class Pkcs12KeyStore {
    static final String LEAF_KEY = """
            -----BEGIN PRIVATE KEY-----
            MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCDUEvE3L4Vzpvvk9QxB
            lQORDyrmX8ocaCDT81k611fSww==
            -----END PRIVATE KEY-----
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

    public static void main(String[] args) throws Exception {
        char[] storePass = "store-secret".toCharArray();
        char[] keyPass = "key-secret".toCharArray();
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate leaf = cf.generateCertificate(stream(LEAF_CERT));
        Certificate inter = cf.generateCertificate(stream(INTERMEDIATE_CA));
        Certificate root = cf.generateCertificate(stream(ROOT_CA));
        PrivateKey leafKey = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(
                Base64.getMimeDecoder().decode(LEAF_KEY.replaceAll("-----[A-Z ]+-----", ""))));
        SecretKey aes = KeyGenerator.getInstance("AES").generateKey();

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null); // a new, empty store
        ks.setKeyEntry("server", leafKey, keyPass, new Certificate[] {leaf, inter});
        ks.setEntry("backup-key", new KeyStore.SecretKeyEntry(aes), new KeyStore.PasswordProtection(keyPass));
        ks.setCertificateEntry("test-root", root);

        Path dir = Files.createTempDirectory("p12-demo");
        Path file = dir.resolve("demo.p12");
        try {
            try (OutputStream out = Files.newOutputStream(file)) {
                ks.store(out, storePass);
            }
            byte[] bytes = Files.readAllBytes(file);
            System.out.printf("written: a DER structure (first byte 0x%02x)%n", bytes[0]);

            KeyStore loaded = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(file)) {
                loaded.load(in, storePass);
            }
            // We stored the server chain as [leaf, intermediate]; the PKCS#12
            // file links certificates by issuer, so on reload the chain also
            // includes the root that sits in the same store.
            List<String> aliases = Collections.list(loaded.aliases());
            Collections.sort(aliases);
            for (String alias : aliases) {
                String kind = loaded.isKeyEntry(alias)
                        ? (loaded.entryInstanceOf(alias, KeyStore.PrivateKeyEntry.class) ? "PrivateKeyEntry" : "SecretKeyEntry")
                        : "TrustedCertificateEntry";
                Certificate[] chain = loaded.getCertificateChain(alias);
                System.out.printf("  %-11s %-23s chain length %d%n", alias, kind, chain == null ? 0 : chain.length);
            }
            System.out.println("private key restored: " + loaded.getKey("server", keyPass).equals(leafKey));
            System.out.println("secret key restored:  " + loaded.getKey("backup-key", keyPass).equals(aes));
            System.out.println("alias for root cert:  " + loaded.getCertificateAlias(root));

            // Wrong key password: the entry stays sealed
            try {
                loaded.getKey("server", "guess".toCharArray());
            } catch (UnrecoverableKeyException e) {
                System.out.println("wrong key password: " + e.getClass().getSimpleName());
            }
            // Wrong store password: the integrity check fails on load
            try (InputStream in = Files.newInputStream(file)) {
                KeyStore.getInstance("PKCS12").load(in, "guess".toCharArray());
            } catch (IOException e) {
                System.out.println("wrong store password: " + e.getClass().getSimpleName()
                        + " caused by " + e.getCause().getClass().getSimpleName());
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
            Arrays.fill(storePass, '\0');
            Arrays.fill(keyPass, '\0');
        }
    }

    static InputStream stream(String pem) {
        return new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII));
    }
}

/* Output:
written: a DER structure (first byte 0x30)
  backup-key  SecretKeyEntry          chain length 0
  server      PrivateKeyEntry         chain length 3
  test-root   TrustedCertificateEntry chain length 0
private key restored: true
secret key restored:  true
alias for root cert:  test-root
wrong key password: UnrecoverableKeyException
wrong store password: IOException caused by UnrecoverableKeyException
*/
