// JWS compact serialization (HS256, ES256) from scratch, with an alg allowlist
//
// A JWS (RFC 7515) in compact form is BASE64URL(header) "." BASE64URL(payload)
// "." BASE64URL(signature), where the signature covers the ASCII bytes of the
// first two parts. HS256 is HMAC-SHA256 with a shared secret; ES256 is ECDSA
// P-256 with SHA-256, and its signature is the raw 64-byte R || S, not the
// DER structure Signature("SHA256withECDSA") produces (the JDK's
// "SHA256withECDSAinP1363Format" emits the raw form). The famous JWT bugs all
// come from trusting the header's "alg": "none" means no signature at all, and
// a verifier that picks HMAC because the header says HS256 can be fed a token
// "signed" with the server's PUBLIC key used as the HMAC secret. The fix: the
// verifier decides the algorithm from its own configuration (key -> one
// allowed alg), rejects anything else, then checks claims such as exp and aud
// (not shown). The HS256 part reproduces RFC 7515 Appendix A.1 exactly.
// The tiny regex "JSON parsing" here is for the demo only; use a real parser.
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class JwsCompact {
    static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    static final Base64.Decoder B64D = Base64.getUrlDecoder();

    static String b64(String s) { return B64.encodeToString(s.getBytes(StandardCharsets.UTF_8)); }

    static byte[] hmac(byte[] key, String signingInput) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
    }

    static String signHs256(String header, String payload, SecretKey key) throws GeneralSecurityException {
        String input = b64(header) + "." + b64(payload);
        return input + "." + B64.encodeToString(hmac(key.getEncoded(), input));
    }

    static String signEs256(String header, String payload, PrivateKey key) throws GeneralSecurityException {
        String input = b64(header) + "." + b64(payload);
        Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
        s.initSign(key);
        s.update(input.getBytes(StandardCharsets.US_ASCII));
        return input + "." + B64.encodeToString(s.sign());
    }

    static String alg(String headerJson) {
        Matcher m = Pattern.compile("\"alg\"\\s*:\\s*\"([^\"]*)\"").matcher(headerJson);
        return m.find() ? m.group(1) : "";
    }

    // NAIVE: trusts the header to choose the algorithm, and has one "key" for everything
    static boolean naiveVerify(String token, Key key) throws GeneralSecurityException {
        String[] p = token.split("\\.", -1);
        String input = p[0] + "." + p[1];
        byte[] sig = B64D.decode(p[2]);
        return switch (alg(new String(B64D.decode(p[0]), StandardCharsets.UTF_8))) {
            case "none" -> true;
            case "HS256" -> MessageDigest.isEqual(hmac(key.getEncoded(), input), sig);
            case "ES256" -> {
                Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
                s.initVerify((PublicKey) key);
                s.update(input.getBytes(StandardCharsets.US_ASCII));
                yield s.verify(sig);
            }
            default -> false;
        };
    }

    // STRICT: the key is configured together with its one allowed algorithm
    record VerificationKey(String alg, Key key) {}

    static String strictVerify(String token, VerificationKey vk) throws GeneralSecurityException {
        String[] p = token.split("\\.", -1);
        if (p.length != 3) return "rejected: not a compact JWS";
        String headerAlg = alg(new String(B64D.decode(p[0]), StandardCharsets.UTF_8));
        if (!headerAlg.equals(vk.alg())) return "rejected: alg " + headerAlg + " not allowed for this key";
        String input = p[0] + "." + p[1];
        byte[] sig = B64D.decode(p[2]);
        boolean ok = switch (vk.alg()) {
            case "HS256" -> MessageDigest.isEqual(hmac(vk.key().getEncoded(), input), sig);
            case "ES256" -> {
                if (sig.length != 64) yield false; // raw R || S only
                Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
                s.initVerify((PublicKey) vk.key());
                s.update(input.getBytes(StandardCharsets.US_ASCII));
                yield s.verify(sig);
            }
            default -> false;
        };
        return ok ? "valid, payload " + new String(B64D.decode(p[1]), StandardCharsets.UTF_8).replaceAll("\\s+", "")
                  : "rejected: bad signature";
    }

    public static void main(String[] args) throws Exception {
        // RFC 7515 A.1: header and payload bytes include CRLF and spaces on purpose
        String header = "{\"typ\":\"JWT\",\r\n \"alg\":\"HS256\"}";
        String payload = "{\"iss\":\"joe\",\r\n \"exp\":1300819380,\r\n \"http://example.com/is_root\":true}";
        SecretKey k = new SecretKeySpec(B64D.decode(
                "AyM1SysPpbyDfgZld3umj1qzKObwVMkoqQ-EstJQLr_T-1qS0gZH75aKtMN3Yj0iPS4hcgUuTwjAzZr1Z9CAow"), "HmacSHA256");
        String hs = signHs256(header, payload, k);
        for (String part : hs.split("\\.")) System.out.println(part);
        System.out.println("matches RFC 7515 A.1: " + hs.endsWith(".dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
        System.out.println("HS256 strict:   " + strictVerify(hs, new VerificationKey("HS256", k)));

        // ES256 with a fresh P-256 key
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair ec = kpg.generateKeyPair();
        String esHeader = "{\"alg\":\"ES256\",\"typ\":\"JWT\"}";
        String es = signEs256(esHeader, "{\"sub\":\"alice\",\"admin\":false}", ec.getPrivate());
        VerificationKey esKey = new VerificationKey("ES256", ec.getPublic());
        System.out.println("ES256 signature bytes: " + B64D.decode(es.split("\\.")[2]).length);
        System.out.println("ES256 strict:   " + strictVerify(es, esKey));

        // Tampering with the payload
        String[] p = es.split("\\.");
        String tampered = p[0] + "." + b64("{\"sub\":\"alice\",\"admin\":true}") + "." + p[2];
        System.out.println("tampered payload, strict: " + strictVerify(tampered, esKey));

        // Attack 1: alg "none" with an empty signature
        String none = b64("{\"alg\":\"none\"}") + "." + b64("{\"sub\":\"alice\",\"admin\":true}") + ".";
        System.out.println("alg=none, naive:  " + (naiveVerify(none, ec.getPublic()) ? "ACCEPTED" : "rejected"));
        System.out.println("alg=none, strict: " + strictVerify(none, esKey));

        // Attack 2: HS256 "signed" with the public key's bytes, which the attacker knows
        SecretKey publicAsSecret = new SecretKeySpec(ec.getPublic().getEncoded(), "HmacSHA256");
        String confused = signHs256("{\"alg\":\"HS256\"}", "{\"sub\":\"alice\",\"admin\":true}", publicAsSecret);
        System.out.println("alg confusion, naive:  " + (naiveVerify(confused, ec.getPublic()) ? "ACCEPTED" : "rejected"));
        System.out.println("alg confusion, strict: " + strictVerify(confused, esKey));
    }
}

/* Output:
eyJ0eXAiOiJKV1QiLA0KICJhbGciOiJIUzI1NiJ9
eyJpc3MiOiJqb2UiLA0KICJleHAiOjEzMDA4MTkzODAsDQogImh0dHA6Ly9leGFtcGxlLmNvbS9pc19yb290Ijp0cnVlfQ
dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
matches RFC 7515 A.1: true
HS256 strict:   valid, payload {"iss":"joe","exp":1300819380,"http://example.com/is_root":true}
ES256 signature bytes: 64
ES256 strict:   valid, payload {"sub":"alice","admin":false}
tampered payload, strict: rejected: bad signature
alg=none, naive:  ACCEPTED
alg=none, strict: rejected: alg none not allowed for this key
alg confusion, naive:  ACCEPTED
alg confusion, strict: rejected: alg HS256 not allowed for this key
*/
