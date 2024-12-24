// OWASP Top 10 2021: A07:2021 Identification and Authentication Failures, token validation (CWE-287, CWE-613)
// https://owasp.org/Top10/A07_2021-Identification_and_Authentication_Failures/
//
// A JWT-style token is header.payload.signature, each part base64url, the
// signature an HMAC over "header.payload". It authenticates a request only
// if the server validates it strictly. The classic failures: letting the
// token's own header choose the algorithm ("alg":"none" means unsigned),
// comparing the MAC with String.equals (its early exit leaks timing),
// forgetting to check expiry and audience, and decoding the payload before
// the signature is verified. Pin the algorithm on the server, verify the
// MAC over the exact received bytes with MessageDigest.isEqual, then check
// exp/aud against a trusted clock. This program uses a fixed clock, a fixed
// demo key and a regex instead of a JSON parser, to stay self-contained.
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class A07_AuthenticationFailures_HmacToken {
    static final byte[] KEY = "demo-key-for-hs256-at-least-32-b".getBytes(StandardCharsets.US_ASCII);
    static final Clock CLOCK = Clock.fixed(Instant.parse("2024-12-24T12:00:00Z"), ZoneOffset.UTC);
    static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    public static void main(String[] args) throws Exception {
        long now = CLOCK.instant().getEpochSecond();
        String hs256 = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

        String valid = sign(hs256, claims("alice", "user", "orders-api", now + 600));
        String expired = sign(hs256, claims("alice", "user", "orders-api", now - 60));
        String otherAudience = sign(hs256, claims("alice", "user", "billing-api", now + 600));
        String[] p = valid.split("\\.");
        String tamperedRole = p[0] + "." + b64(claims("alice", "admin", "orders-api", now + 600)) + "." + p[2];
        String algNone = b64("{\"alg\":\"none\"}") + "." + b64(claims("alice", "admin", "orders-api", now + 600)) + ".";

        String[][] cases = {
                {"valid", valid}, {"expired", expired}, {"other audience", otherAudience},
                {"role changed to admin", tamperedRole}, {"alg none, role admin", algNone}, {"garbage", "a.b"}};
        System.out.printf("%-24s %-26s %s%n", "token", "noncompliant", "compliant");
        for (String[] c : cases) {
            System.out.printf("%-24s %-26s %s%n", c[0], validateNoncompliant(c[1]), validateCompliant(c[1]));
        }
    }

    // Noncompliant: trusts the header's alg, uses equals(), ignores exp and aud
    static String validateNoncompliant(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            if (!header.contains("\"alg\":\"none\"")) {
                String expected = b64(hmac(parts[0] + "." + parts[1]));
                if (!expected.equals(parts[2])) return "rejected";
            }
            return "accepted as " + claim(payload, "role");
        } catch (Exception e) {
            return "rejected";
        }
    }

    // Compliant: fixed algorithm, constant-time MAC check first, then claims
    static String validateCompliant(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) return "rejected: malformed";
            String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            if (!"HS256".equals(claim(header, "alg"))) return "rejected: algorithm";
            byte[] expected = hmac(parts[0] + "." + parts[1]);
            byte[] actual = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) return "rejected: signature";
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            if (!"orders-api".equals(claim(payload, "aud"))) return "rejected: audience";
            long exp = Long.parseLong(claim(payload, "exp"));
            if (CLOCK.instant().getEpochSecond() >= exp) return "rejected: expired";
            return "accepted as " + claim(payload, "role");
        } catch (RuntimeException e) {
            return "rejected: malformed";
        }
    }

    static String claims(String sub, String role, String aud, long exp) {
        return "{\"sub\":\"" + sub + "\",\"role\":\"" + role + "\",\"aud\":\"" + aud + "\",\"exp\":" + exp + "}";
    }

    static String sign(String header, String payload) {
        String signingInput = b64(header) + "." + b64(payload);
        return signingInput + "." + b64(hmac(signingInput));
    }

    static byte[] hmac(String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(KEY, "HmacSHA256"));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String b64(String s) {
        return b64(s.getBytes(StandardCharsets.UTF_8));
    }

    static String b64(byte[] b) {
        return B64.encodeToString(b);
    }

    // Reads "name":"value" or "name":123 from flat JSON (demo only)
    static String claim(String json, String name) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(name) + "\":(?:\"([^\"]*)\"|(\\d+))").matcher(json);
        if (!m.find()) return null;
        return m.group(1) != null ? m.group(1) : m.group(2);
    }
}

/* Output:
token                    noncompliant               compliant
valid                    accepted as user           accepted as user
expired                  accepted as user           rejected: expired
other audience           accepted as user           rejected: audience
role changed to admin    rejected                   rejected: signature
alg none, role admin     accepted as admin          rejected: algorithm
garbage                  rejected                   rejected: malformed
*/
