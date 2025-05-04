// Certificate revocation: reading a CRL and checking it during PKIX validation
//
// A certificate stays cryptographically valid until it expires, even after
// its private key leaks. Revocation is how a CA takes it back. A CRL (RFC
// 5280 section 5) is a list the CA signs: issuer, thisUpdate, nextUpdate, and
// for every revoked certificate its serial number, revocation date and an
// optional reason (keyCompromise, superseded, ...). OCSP asks the CA's
// responder about one certificate instead; OCSP stapling lets the server
// attach a fresh response to its handshake. The weak spot is failure
// handling: when revocation data cannot be fetched, most clients "soft-fail"
// and accept the certificate, which an attacker who can block traffic
// exploits; that is why short-lived certificates are gaining ground. In Java:
// CertificateFactory.generateCRL parses a CRL, X509CRL.verify checks its
// signature, and PKIXParameters with revocation enabled plus a CertStore
// holding CRLs makes CertPathValidator reject revoked certificates; a
// PKIXRevocationChecker sets the policy (CRLs or OCSP first, end entity only,
// soft-fail or not). Everything here is offline: the CRL below was made
// with keytool -gencrl by the test intermediate CA (the same test PKI as
// Tls/LoopbackHandshake.java), revoking "mallory" for key compromise.
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertStore;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

public class CrlRevocation {
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
    static final String ALICE = """
            -----BEGIN CERTIFICATE-----
            MIIB8jCCAZegAwIBAgIJAMAlMTNdtMjmMAoGCCqGSM49BAMCMD0xEjAQBgNVBAoT
            CWphdmEtYXRvejEnMCUGA1UEAxMeamF2YS1hdG96IFRlc3QgSW50ZXJtZWRpYXRl
            IENBMB4XDTI1MDEwMTAwMDAwMFoXDTM0MTIzMDAwMDAwMFowMzESMBAGA1UEChMJ
            amF2YS1hdG96MR0wGwYDVQQDExRhbGljZS5qYXZhLWF0b3oudGVzdDBZMBMGByqG
            SM49AgEGCCqGSM49AwEHA0IABAF/x3g7gdmmDwLWFqR/D65IZT18Tvgk5I6431aT
            ShMFSGS3wj6CkO0OMPPBWRarlKPwplKRaz3zTDSGHQ3D/0ejgYkwgYYwHQYDVR0O
            BBYEFEMV9CpixF1Tkn/sbEE7ycmq2S1VMA4GA1UdDwEB/wQEAwIHgDAfBgNVHREE
            GDAWghRhbGljZS5qYXZhLWF0b3oudGVzdDAfBgNVHSMEGDAWgBSM9YboO7nmghfe
            k9+dgkcQEBswMjATBgNVHSUEDDAKBggrBgEFBQcDATAKBggqhkjOPQQDAgNJADBG
            AiEAy6u9uorH4BHFNIDOOCEpgoGBilQXq1YwT8PNXOaurEoCIQC+w0GSMc7rG3mo
            x8VK4aMUxOQE5xCofLJab8ewKRHDpQ==
            -----END CERTIFICATE-----
            """;
    static final String MALLORY = """
            -----BEGIN CERTIFICATE-----
            MIIB9TCCAZqgAwIBAgIIaFk+ANY011cwCgYIKoZIzj0EAwIwPTESMBAGA1UEChMJ
            amF2YS1hdG96MScwJQYDVQQDEx5qYXZhLWF0b3ogVGVzdCBJbnRlcm1lZGlhdGUg
            Q0EwHhcNMjUwMTAxMDAwMDAwWhcNMzQxMjMwMDAwMDAwWjA1MRIwEAYDVQQKEwlq
            YXZhLWF0b3oxHzAdBgNVBAMTFm1hbGxvcnkuamF2YS1hdG96LnRlc3QwWTATBgcq
            hkjOPQIBBggqhkjOPQMBBwNCAASTX6TVNQTugThJ91J2cntjif9n7wKmOgSzc8Rd
            wDgISBjhA/KYHtfDU/I/HEidFgFe81NIvnTaKgZgkD98IpC7o4GLMIGIMB0GA1Ud
            DgQWBBQ9N9B30fWQXhGe1g/bkENmcWPqszAOBgNVHQ8BAf8EBAMCB4AwIQYDVR0R
            BBowGIIWbWFsbG9yeS5qYXZhLWF0b3oudGVzdDAfBgNVHSMEGDAWgBSM9YboO7nm
            ghfek9+dgkcQEBswMjATBgNVHSUEDDAKBggrBgEFBQcDATAKBggqhkjOPQQDAgNJ
            ADBGAiEArcVbJGSAo+n99GQ7vGWNumoR6YAowuzN2MEV6lZyNGECIQCYT0HTYBv2
            +2M4CAadXjvyn6C7J4SKwcOqazGIAluaVw==
            -----END CERTIFICATE-----
            """;
    static final String CRL = """
            -----BEGIN X509 CRL-----
            MIHwMIGXAgEBMAoGCCqGSM49BAMCMD0xEjAQBgNVBAoTCWphdmEtYXRvejEnMCUG
            A1UEAxMeamF2YS1hdG96IFRlc3QgSW50ZXJtZWRpYXRlIENBFw0yNTA1MDEwMDAw
            MDBaFw0yNTA1MzEwMDAwMDBaMCkwJwIIaFk+ANY011cXDTI1MDUwMTAwMDAwMFow
            DDAKBgNVHRUEAwoBATAKBggqhkjOPQQDAgNIADBFAiEA/j8f3Mtg2+s7HuMhvQKB
            1p5dPxJAXgMtMeHPR6KbCw4CICbOH097OzqD/1w7Z6cwGdn5hhM8PZQtdI3hYsra
            5Y1P
            -----END X509 CRL-----
            """;

    public static void main(String[] args) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate root = cert(cf, ROOT_CA), inter = cert(cf, INTERMEDIATE_CA);
        X509Certificate alice = cert(cf, ALICE), mallory = cert(cf, MALLORY);
        X509CRL crl = (X509CRL) cf.generateCRL(new ByteArrayInputStream(CRL.getBytes(StandardCharsets.US_ASCII)));

        System.out.println("CRL version " + crl.getVersion() + ", issuer: " + crl.getIssuerX500Principal().getName());
        System.out.println("thisUpdate " + crl.getThisUpdate().toInstant() + ", nextUpdate " + crl.getNextUpdate().toInstant());
        for (X509CRLEntry e : crl.getRevokedCertificates()) {
            System.out.println("revoked serial " + HexFormat.of().toHexDigits(e.getSerialNumber().longValue())
                    + " on " + e.getRevocationDate().toInstant() + ", reason " + e.getRevocationReason());
        }
        crl.verify(inter.getPublicKey()); // throws if the CA did not sign it
        System.out.println("CRL signature verified with the intermediate CA key");
        byte[] forged = crl.getEncoded();
        forged[forged.length - 80] ^= 1; // somewhere in the signed content
        try {
            ((X509CRL) cf.generateCRL(new ByteArrayInputStream(forged))).verify(inter.getPublicKey());
            System.out.println("forged CRL verified?!");
        } catch (Exception e) {
            System.out.println("modified CRL: rejected");
        }
        System.out.println("isRevoked(alice):   " + crl.isRevoked(alice));
        System.out.println("isRevoked(mallory): " + crl.isRevoked(mallory));

        // Full PKIX validation with revocation checking, at a fixed time inside the CRL's window
        Date during = Date.from(Instant.parse("2025-05-04T12:00:00Z"));
        Date stale = Date.from(Instant.parse("2025-06-15T12:00:00Z")); // after nextUpdate
        System.out.println("alice   (fresh CRL): " + validate(cf, root, inter, alice, crl, during, false));
        System.out.println("mallory (fresh CRL): " + validate(cf, root, inter, mallory, crl, during, false));
        System.out.println("alice   (stale CRL): " + validate(cf, root, inter, alice, crl, stale, false));
        System.out.println("alice   (stale CRL, SOFT_FAIL): " + validate(cf, root, inter, alice, crl, stale, true));
        System.out.println("mallory (stale CRL, SOFT_FAIL): " + validate(cf, root, inter, mallory, crl, stale, true)
                + "  <- a revoked certificate passes: the soft-fail problem");
    }

    static String validate(CertificateFactory cf, X509Certificate root, X509Certificate inter, X509Certificate leaf,
                           X509CRL crl, Date at, boolean softFail) throws Exception {
        PKIXParameters params = new PKIXParameters(Set.of(new TrustAnchor(root, null)));
        params.setDate(at);
        params.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(List.of(crl))));
        CertPathValidator validator = CertPathValidator.getInstance("PKIX");
        PKIXRevocationChecker rc = (PKIXRevocationChecker) validator.getRevocationChecker();
        // CRLs only (no network fetches), and only the leaf: the root's CRL for the intermediate is not available offline
        EnumSet<PKIXRevocationChecker.Option> opts = EnumSet.of(PKIXRevocationChecker.Option.PREFER_CRLS,
                PKIXRevocationChecker.Option.NO_FALLBACK, PKIXRevocationChecker.Option.ONLY_END_ENTITY);
        if (softFail) opts.add(PKIXRevocationChecker.Option.SOFT_FAIL);
        rc.setOptions(opts);
        params.addCertPathChecker(rc);
        try {
            validator.validate(cf.generateCertPath(List.of(leaf, inter)), params);
            return "valid";
        } catch (CertPathValidatorException e) {
            return "rejected, reason " + e.getReason();
        }
    }

    static X509Certificate cert(CertificateFactory cf, String pem) throws Exception {
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
    }
}

/* Output:
CRL version 2, issuer: CN=java-atoz Test Intermediate CA,O=java-atoz
thisUpdate 2025-05-01T00:00:00Z, nextUpdate 2025-05-31T00:00:00Z
revoked serial 68593e00d634d757 on 2025-05-01T00:00:00Z, reason KEY_COMPROMISE
CRL signature verified with the intermediate CA key
modified CRL: rejected
isRevoked(alice):   false
isRevoked(mallory): true
alice   (fresh CRL): valid
mallory (fresh CRL): rejected, reason REVOKED
alice   (stale CRL): rejected, reason UNDETERMINED_REVOCATION_STATUS
alice   (stale CRL, SOFT_FAIL): valid
mallory (stale CRL, SOFT_FAIL): valid  <- a revoked certificate passes: the soft-fail problem
*/
