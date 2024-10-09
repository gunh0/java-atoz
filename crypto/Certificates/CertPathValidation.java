// Validating a certificate chain with CertPathValidator (PKIX, RFC 5280)
//
// Checking one signature is not certificate validation. PKIX validation
// walks the chain from a trust anchor down to the end entity and checks, for
// every link: the issuer's signature, the validity period at the check
// time, that every issuer is a CA (basicConstraints) allowed to sign
// certificates (keyUsage keyCertSign), path length limits, name and policy
// constraints, and revocation. Skipping the CA check is a famous bug class
// (2002: browsers accepted any site's certificate as an issuer). The test
// chain below (root -> intermediate -> leaf "localhost") was made with
// keytool, plus a certificate for "bank.example" that the LEAF signed.
// Revocation (OCSP/CRL) is switched off only because this runs offline;
// production validation should keep it on.
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class CertPathValidation {
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
    static final String BANK_CERT = """
            -----BEGIN CERTIFICATE-----
            MIIBpDCCAUugAwIBAgIISs2PVBXQM88wCgYIKoZIzj0EAwIwKDESMBAGA1UEChMJ
            amF2YS1hdG96MRIwEAYDVQQDEwlsb2NhbGhvc3QwHhcNMjQwMTAxMDAwMDAwWhcN
            MzIwMzE5MDAwMDAwWjAsMRMwEQYDVQQKEwpOb3QgQSBCYW5rMRUwEwYDVQQDEwxi
            YW5rLmV4YW1wbGUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQnUEmKYa50u2Mz
            7obnfSe7BLTznDgcxOcjOc9M38Q88nsot+Zj+pEE5tP7BWS9uWcyERPFP71XOGTF
            CbVNvrEvo1swWTAdBgNVHQ4EFgQUvWwKMyQ7e8q4U8GSXKtUOAnolcswFwYDVR0R
            BBAwDoIMYmFuay5leGFtcGxlMB8GA1UdIwQYMBaAFA/oNdqMpHqK+P4fj9RwT8bS
            O6VtMAoGCCqGSM49BAMCA0cAMEQCIG75uzIRzTbFjDNFdNonq0HAzw8t7Yk0Z+ZX
            0nM+YTM+AiB6oI8glwViZVBDMYZYfo1D0OSO+Xf98iy+uUMPGBIX9g==
            -----END CERTIFICATE-----
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

    static final CertificateFactory CF;
    static {
        try {
            CF = CertificateFactory.getInstance("X.509");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static void main(String[] args) throws Exception {
        X509Certificate root = cert(ROOT_CA), inter = cert(INTERMEDIATE_CA), leaf = cert(LEAF_CERT);
        X509Certificate bank = cert(BANK_CERT), evil = cert(EVIL_CERT);
        String june2024 = "2024-06-01T00:00:00Z";

        validate("leaf <- intermediate <- root", List.of(leaf, inter), root, june2024);
        validate("leaf without its intermediate", List.of(leaf), root, june2024);
        validate("trusting the wrong root", List.of(leaf, inter), evil, june2024);
        validate("checked in 2045", List.of(leaf, inter), root, "2045-01-01T00:00:00Z");
        // The leaf's signature on bank.example is mathematically fine, but the
        // leaf may not issue certificates: no keyCertSign, and it is not a CA.
        validate("bank.example signed by the leaf", List.of(bank, leaf, inter), root, june2024);
        // A self-signed certificate is only "valid" if you trust it directly
        validate("self-signed, trusted as anchor", List.of(evil), evil, june2024);
    }

    static void validate(String label, List<X509Certificate> chain, X509Certificate anchor, String when) throws Exception {
        CertPath path = CF.generateCertPath(chain);
        PKIXParameters params = new PKIXParameters(Set.of(new TrustAnchor(anchor, null)));
        params.setRevocationEnabled(false);
        params.setDate(Date.from(Instant.parse(when))); // fixed instant -> stable output
        try {
            PKIXCertPathValidatorResult r = (PKIXCertPathValidatorResult) CertPathValidator.getInstance("PKIX").validate(path, params);
            System.out.printf("%-34s VALID (anchor %s)%n", label, r.getTrustAnchor().getTrustedCert().getSubjectX500Principal().getName());
        } catch (CertPathValidatorException e) {
            System.out.printf("%-34s INVALID, reason %s at index %d%n", label, e.getReason(), e.getIndex());
        }
    }

    static X509Certificate cert(String pem) throws Exception {
        return (X509Certificate) CF.generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
    }
}

/* Output:
leaf <- intermediate <- root       VALID (anchor CN=java-atoz Test Root CA,O=java-atoz)
leaf without its intermediate      INVALID, reason NO_TRUST_ANCHOR at index -1
trusting the wrong root            INVALID, reason NO_TRUST_ANCHOR at index -1
checked in 2045                    INVALID, reason EXPIRED at index 1
bank.example signed by the leaf    INVALID, reason INVALID_KEY_USAGE at index 1
self-signed, trusted as anchor     VALID (anchor CN=localhost,O=Mallory)
*/
