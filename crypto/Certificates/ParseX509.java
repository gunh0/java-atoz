// Reading an X.509 certificate: fields, validity, signature, fingerprint
//
// A certificate is a signed statement "this public key belongs to this
// name, for these purposes, during this period". The JDK can parse and
// verify certificates (CertificateFactory "X.509") but has no public API to
// create them; the one below was made once with keytool and pasted in as
// PEM. What to look at when reviewing one: subject and issuer (equal here:
// self-signed), the validity window, the subject alternative names (what
// hostname checks actually use; CN is legacy), key usage and basic
// constraints (may it sign other certificates?), the signature algorithm,
// and the fingerprint, a hash of the whole DER encoding that identifies this
// exact certificate (pin and compare SHA-256 fingerprints, never MD5/SHA-1).
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

public class ParseX509 {
    static final String SELF_SIGNED = """
            -----BEGIN CERTIFICATE-----
            MIIB6TCCAY+gAwIBAgIIW49XU3GPMwswCgYIKoZIzj0EAwIwUDELMAkGA1UEBhMC
            S1IxEjAQBgNVBAoTCWphdmEtYXRvejENMAsGA1UECxMERG9jczEeMBwGA1UEAxMV
            c2lnbmVyLmphdmEtYXRvei50ZXN0MB4XDTI0MDEwMTAwMDAwMFoXDTMzMTIyOTAw
            MDAwMFowUDELMAkGA1UEBhMCS1IxEjAQBgNVBAoTCWphdmEtYXRvejENMAsGA1UE
            CxMERG9jczEeMBwGA1UEAxMVc2lnbmVyLmphdmEtYXRvei50ZXN0MFkwEwYHKoZI
            zj0CAQYIKoZIzj0DAQcDQgAEdz5ijSupyhL+Mzor2+3R1c5XbVQozre1/LhE+spJ
            /uLXNxkaUbeUZtx5p7LwYvjpuZ1oCzexRA/Vewmfeueqt6NTMFEwHQYDVR0OBBYE
            FHhKNK66yD9AIb/8vw92UYLh3xw6MA4GA1UdDwEB/wQEAwIHgDAgBgNVHREEGTAX
            ghVzaWduZXIuamF2YS1hdG96LnRlc3QwCgYIKoZIzj0EAwIDSAAwRQIgde2u+47q
            hcHHIsciUNKRtVQGXPJ6/JEZ0Ty+sacw8tECIQCoFV0Yi3m0TLmrZt+MNakNH3TO
            BWetRtq6tDEfmUzsxA==
            -----END CERTIFICATE-----
            """;

    public static void main(String[] args) throws Exception {
        X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(SELF_SIGNED.getBytes(StandardCharsets.US_ASCII)));

        System.out.println("version:      v" + cert.getVersion());
        System.out.println("serial:       " + cert.getSerialNumber().toString(16));
        System.out.println("subject:      " + cert.getSubjectX500Principal().getName());
        System.out.println("issuer:       " + cert.getIssuerX500Principal().getName());
        System.out.println("not before:   " + cert.getNotBefore().toInstant());
        System.out.println("not after:    " + cert.getNotAfter().toInstant());
        System.out.println("public key:   " + cert.getPublicKey().getAlgorithm());
        System.out.println("signature:    " + cert.getSigAlgName() + " (OID " + cert.getSigAlgOID() + ")");

        // Extensions
        List<String> sans = cert.getSubjectAlternativeNames().stream()
                .map(e -> (Integer) e.get(0) == 2 ? "DNS:" + e.get(1) : "type " + e.get(0) + ":" + e.get(1)).toList();
        System.out.println("SAN:          " + sans);
        boolean[] ku = cert.getKeyUsage();
        String[] names = {"digitalSignature", "nonRepudiation", "keyEncipherment", "dataEncipherment",
                "keyAgreement", "keyCertSign", "cRLSign", "encipherOnly", "decipherOnly"};
        StringBuilder usage = new StringBuilder();
        for (int i = 0; i < ku.length && i < names.length; i++) if (ku[i]) usage.append(names[i]).append(' ');
        System.out.println("key usage:    " + usage.toString().strip()
                + (cert.getCriticalExtensionOIDs().contains("2.5.29.15") ? " (critical)" : ""));
        System.out.println("CA:           " + (cert.getBasicConstraints() >= 0));

        // Validity at chosen instants (instead of "now", to keep the output stable)
        for (String when : new String[] {"2023-06-01T00:00:00Z", "2024-04-01T00:00:00Z", "2034-01-01T00:00:00Z"}) {
            try {
                cert.checkValidity(Date.from(Instant.parse(when)));
                System.out.println("valid at " + when + ": yes");
            } catch (CertificateException e) {
                System.out.println("valid at " + when + ": " + e.getClass().getSimpleName());
            }
        }

        // Signature: self-signed, so it verifies with its own public key...
        cert.verify(cert.getPublicKey());
        System.out.println("signature verifies with own key: true");
        // ...and not with anybody else's
        try {
            cert.verify(KeyPairGenerator.getInstance("EC").generateKeyPair().getPublic());
            System.out.println("verifies with a random key?!");
        } catch (SignatureException | InvalidKeyException e) {
            System.out.println("signature with another key: " + e.getClass().getSimpleName());
        }

        // Fingerprint: SHA-256 over the DER encoding (what browsers display)
        byte[] fp = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
        System.out.println("SHA-256 fingerprint: " + HexFormat.ofDelimiter(":").withUpperCase().formatHex(fp));
    }
}

/* Output:
version:      v3
serial:       5b8f5753718f330b
subject:      CN=signer.java-atoz.test,OU=Docs,O=java-atoz,C=KR
issuer:       CN=signer.java-atoz.test,OU=Docs,O=java-atoz,C=KR
not before:   2024-01-01T00:00:00Z
not after:    2033-12-29T00:00:00Z
public key:   EC
signature:    SHA256withECDSA (OID 1.2.840.10045.4.3.2)
SAN:          [DNS:signer.java-atoz.test]
key usage:    digitalSignature (critical)
CA:           false
valid at 2023-06-01T00:00:00Z: CertificateNotYetValidException
valid at 2024-04-01T00:00:00Z: yes
valid at 2034-01-01T00:00:00Z: CertificateExpiredException
signature verifies with own key: true
signature with another key: SignatureException
SHA-256 fingerprint: EF:79:E3:43:4E:FC:92:16:C3:2E:CC:8B:C2:44:74:AC:A2:C5:AF:96:9C:13:53:1F:FE:ED:F0:15:3A:04:1F:BE
*/
