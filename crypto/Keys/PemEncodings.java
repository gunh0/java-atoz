// Key encodings: X.509 SubjectPublicKeyInfo, PKCS#8 and PEM, via KeyFactory
//
// Key.getEncoded() returns DER bytes in a standard container: public keys
// as X.509 SubjectPublicKeyInfo (getFormat() "X.509": algorithm OID +
// key), private keys as PKCS#8 PrivateKeyInfo ("PKCS#8"). PEM is just that
// DER in Base64 with 64-character lines between "-----BEGIN PUBLIC KEY-----"
// / "-----BEGIN PRIVATE KEY-----" lines (RFC 7468), the format OpenSSL,
// keytool exports and most config files use. To turn bytes back into a key,
// KeyFactory.getInstance(alg) with X509EncodedKeySpec / PKCS8EncodedKeySpec.
// An unencrypted PKCS#8 PEM is a plaintext secret: keep it in a key store or
// secret manager, never in source control. (The key below is a throwaway
// test key generated for this repository, which is exactly why it may sit
// in a source file.)
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class PemEncodings {
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

    public static void main(String[] args) throws Exception {
        // 1. Parse a PKCS#8 PEM private key
        PrivateKey priv = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(pemBody(LEAF_KEY, "PRIVATE KEY")));
        System.out.println("private key: algorithm " + priv.getAlgorithm() + ", format " + priv.getFormat()
                + ", curve field " + ((ECPrivateKey) priv).getParams().getCurve().getField().getFieldSize() + " bits");

        // Does it belong to the certificate? Sign with it, verify with the cert's public key.
        X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(LEAF_CERT.getBytes(StandardCharsets.US_ASCII)));
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(priv);
        s.update(new byte[] {1, 2, 3});
        byte[] sig = s.sign();
        s.initVerify(cert.getPublicKey());
        s.update(new byte[] {1, 2, 3});
        System.out.println("private key matches certificate's public key: " + s.verify(sig));
        System.out.println("certificate public key: format " + cert.getPublicKey().getFormat() + ", "
                + cert.getPublicKey().getEncoded().length + " bytes DER (P-256 SubjectPublicKeyInfo)");

        // 2. Write a fresh RSA key pair as PEM and read it back
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        String pubPem = toPem("PUBLIC KEY", kp.getPublic().getEncoded());
        String privPem = toPem("PRIVATE KEY", kp.getPrivate().getEncoded());
        System.out.println();
        System.out.println(pubPem.lines().findFirst().orElseThrow() + " ... " + pubPem.lines().count() + " lines, longest "
                + pubPem.lines().mapToInt(String::length).max().orElse(0) + " chars");
        System.out.println(privPem.lines().findFirst().orElseThrow() + " ... longest line "
                + privPem.lines().mapToInt(String::length).max().orElse(0) + " chars");

        KeyFactory rsa = KeyFactory.getInstance("RSA");
        PublicKey pub2 = rsa.generatePublic(new X509EncodedKeySpec(pemBody(pubPem, "PUBLIC KEY")));
        PrivateKey priv2 = rsa.generatePrivate(new PKCS8EncodedKeySpec(pemBody(privPem, "PRIVATE KEY")));
        System.out.println("public key round trip equal:  " + pub2.equals(kp.getPublic()));
        System.out.println("private key round trip equal: " + priv2.equals(kp.getPrivate()));

        // Wrong container for the bytes -> InvalidKeySpecException
        try {
            rsa.generatePublic(new X509EncodedKeySpec(pemBody(privPem, "PRIVATE KEY")));
        } catch (InvalidKeySpecException e) {
            System.out.println("PKCS#8 bytes read as X.509: " + e.getClass().getSimpleName());
        }
    }

    static String toPem(String type, byte[] der) {
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + b64 + "\n-----END " + type + "-----\n";
    }

    static byte[] pemBody(String pem, String type) {
        String begin = "-----BEGIN " + type + "-----", end = "-----END " + type + "-----";
        int from = pem.indexOf(begin), to = pem.indexOf(end);
        if (from < 0 || to < from) throw new IllegalArgumentException("no " + type + " block");
        return Base64.getMimeDecoder().decode(pem.substring(from + begin.length(), to));
    }
}

/* Output:
private key: algorithm EC, format PKCS#8, curve field 256 bits
private key matches certificate's public key: true
certificate public key: format X.509, 91 bytes DER (P-256 SubjectPublicKeyInfo)

-----BEGIN PUBLIC KEY----- ... 9 lines, longest 64 chars
-----BEGIN PRIVATE KEY----- ... longest line 64 chars
public key round trip equal:  true
private key round trip equal: true
PKCS#8 bytes read as X.509: InvalidKeySpecException
*/
