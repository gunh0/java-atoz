// OWASP Top 10 2021: A02:2021 Cryptographic Failures (CWE-326, CWE-327, CWE-297)
// https://owasp.org/Top10/A02_2021-Cryptographic_Failures/
//
// TLS is only as strong as its configuration. Hand-tuned settings that were
// copied "to make the old client work" tend to re-enable what the JDK has
// long turned off: SSLv3, TLS 1.0 and 1.1, cipher suites without
// encryption (NULL), without authentication (anon), export-grade, RC4,
// 3DES, or static RSA key exchange (no forward secrecy). A separate, easy
// to miss gap: raw SSLSocket and SSLEngine do NOT check that the
// certificate matches the host name unless the endpoint identification
// algorithm is set to "HTTPS" (HttpsURLConnection and HttpClient do it).
// The fix: audit the effective SSLParameters, allow TLS 1.3/1.2 with AEAD,
// forward-secret suites only, and always set endpoint identification.
// The demo inspects parameter objects only; no connection is made.
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

public class A02_CryptographicFailures_WeakTlsConfig {
    static final List<String> WEAK_PROTOCOLS = List.of("SSLv2Hello", "SSLv3", "TLSv1", "TLSv1.1");

    public static void main(String[] args) throws Exception {
        // Noncompliant: a "compatibility" configuration found in old code
        SSLParameters legacy = new SSLParameters(
                new String[] {
                    "SSL_RSA_WITH_RC4_128_MD5",
                    "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
                    "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
                    "TLS_DH_anon_WITH_AES_128_CBC_SHA",
                    "TLS_RSA_WITH_NULL_SHA256",
                    "TLS_RSA_WITH_AES_128_CBC_SHA",
                    "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
                    "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"},
                new String[] {"SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2"});
        report("noncompliant legacy parameters", legacy);

        // Compliant: TLS 1.3 and 1.2, AEAD + ECDHE only, host name verification on
        SSLParameters hardened = new SSLParameters(
                new String[] {
                    "TLS_AES_256_GCM_SHA384",
                    "TLS_AES_128_GCM_SHA256",
                    "TLS_CHACHA20_POLY1305_SHA256",
                    "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                    "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                    "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256"},
                new String[] {"TLSv1.3", "TLSv1.2"});
        hardened.setEndpointIdentificationAlgorithm("HTTPS");
        report("compliant hardened parameters", hardened);

        // Apply it to a real engine and audit what the engine reports back
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, null);
        SSLEngine engine = context.createSSLEngine("api.example.com", 443);
        report("engine with JDK defaults (only the host-name check)", hostNameOnly(engine.getSSLParameters()));
        engine.setSSLParameters(hardened);
        report("engine after setSSLParameters(hardened)", engine.getSSLParameters());

        // The JDK defaults themselves contain none of the broken classes
        SSLParameters defaults = SSLContext.getDefault().getDefaultSSLParameters();
        List<String> brokenDefaults = new ArrayList<>();
        for (String p : defaults.getProtocols()) {
            if (WEAK_PROTOCOLS.contains(p)) {
                brokenDefaults.add(p);
            }
        }
        for (String s : defaults.getCipherSuites()) {
            if (brokenSuite(s) != null) {
                brokenDefaults.add(s);
            }
        }
        System.out.println("JDK default protocols and suites flagged as broken: " + brokenDefaults.size());
    }

    // Only the endpoint identification setting, so the output does not depend
    // on the JDK version's default suite list
    static SSLParameters hostNameOnly(SSLParameters p) {
        SSLParameters copy = new SSLParameters(new String[0], new String[0]);
        copy.setEndpointIdentificationAlgorithm(p.getEndpointIdentificationAlgorithm());
        return copy;
    }

    static void report(String title, SSLParameters p) {
        List<String> findings = audit(p);
        System.out.println(title + ": " + (findings.isEmpty() ? "no findings" : findings.size() + (findings.size() == 1 ? " finding" : " findings")));
        findings.forEach(f -> System.out.println("  - " + f));
    }

    static List<String> audit(SSLParameters p) {
        List<String> findings = new ArrayList<>();
        for (String protocol : p.getProtocols()) {
            if (WEAK_PROTOCOLS.contains(protocol)) {
                findings.add("protocol " + protocol + " is obsolete");
            }
        }
        for (String suite : p.getCipherSuites()) {
            String why = brokenSuite(suite);
            if (why == null && suite.startsWith("TLS_RSA_WITH_")) {
                why = "static RSA key exchange, no forward secrecy";
            }
            if (why == null && suite.contains("_CBC_") && suite.endsWith("_SHA")) {
                why = "CBC with HMAC-SHA1 (MAC-then-encrypt, padding oracles)";
            }
            if (why != null) {
                findings.add(suite + ": " + why);
            }
        }
        if (!"HTTPS".equals(p.getEndpointIdentificationAlgorithm())) {
            findings.add("endpoint identification algorithm is " + p.getEndpointIdentificationAlgorithm()
                    + ": certificate host name is not checked");
        }
        return findings;
    }

    // Classes of suites that are broken outright
    static String brokenSuite(String suite) {
        if (suite.contains("_NULL_")) {
            return "no encryption";
        }
        if (suite.contains("_anon_")) {
            return "no authentication";
        }
        if (suite.contains("_EXPORT")) {
            return "export-grade keys";
        }
        if (suite.contains("_RC4_")) {
            return "RC4 is broken";
        }
        if (suite.contains("_3DES_") || suite.contains("_DES_")) {
            return "64-bit block cipher (Sweet32)";
        }
        if (suite.endsWith("_MD5")) {
            return "MD5 MAC";
        }
        return null;
    }
}

/* Output:
noncompliant legacy parameters: 11 findings
  - protocol SSLv3 is obsolete
  - protocol TLSv1 is obsolete
  - protocol TLSv1.1 is obsolete
  - SSL_RSA_WITH_RC4_128_MD5: RC4 is broken
  - SSL_RSA_WITH_3DES_EDE_CBC_SHA: 64-bit block cipher (Sweet32)
  - SSL_RSA_EXPORT_WITH_RC4_40_MD5: export-grade keys
  - TLS_DH_anon_WITH_AES_128_CBC_SHA: no authentication
  - TLS_RSA_WITH_NULL_SHA256: no encryption
  - TLS_RSA_WITH_AES_128_CBC_SHA: static RSA key exchange, no forward secrecy
  - TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA: CBC with HMAC-SHA1 (MAC-then-encrypt, padding oracles)
  - endpoint identification algorithm is null: certificate host name is not checked
compliant hardened parameters: no findings
engine with JDK defaults (only the host-name check): 1 finding
  - endpoint identification algorithm is null: certificate host name is not checked
engine after setSSLParameters(hardened): no findings
JDK default protocols and suites flagged as broken: 0
*/
