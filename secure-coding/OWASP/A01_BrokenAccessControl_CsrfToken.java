// OWASP Top 10 2021: A01:2021 Broken Access Control, cross-site request forgery (CWE-352)
// https://owasp.org/Top10/A01_2021-Broken_Access_Control/
//
// A browser attaches a site's cookies to every request to that site, even
// one triggered by a form on another site. Without a second proof that the
// request came from the site's own page, evil.example can make a logged-in
// user's browser POST /transfer. The usual proof is a CSRF token: a random
// per-session value embedded in the page and checked on every
// state-changing request. Common mistakes are accepting a request when the
// token is missing, using a predictable or global token, not binding it to
// the session, and comparing with String.equals, whose running time leaks
// how many leading characters matched. Generate tokens with SecureRandom,
// require them, compare with MessageDigest.isEqual, and add
// SameSite cookies and an Origin check as further layers.
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class A01_BrokenAccessControl_CsrfToken {
    static final SecureRandom RANDOM = new SecureRandom();
    static final Map<String, String> TOKENS_BY_SESSION = new HashMap<>();
    static final Set<String> TRUSTED_ORIGINS = Set.of("https://bank.example");

    public static void main(String[] args) {
        String alice = "session-alice", mallory = "session-mallory";
        String aliceToken = issueToken(alice);
        String malloryToken = issueToken(mallory);
        System.out.println("token: " + aliceToken.length() + " URL-safe chars from 32 random bytes; distinct per session: "
                + !aliceToken.equals(malloryToken));

        record Req(String label, String session, String token, String origin) {}
        Req[] requests = {
                new Req("form on bank.example", alice, aliceToken, "https://bank.example"),
                new Req("forged form, no token", alice, null, "https://evil.example"),
                new Req("forged, attacker's own token", alice, malloryToken, "https://evil.example"),
                new Req("forged, guessed token", alice, "A".repeat(43), "https://evil.example"),
                new Req("right token, foreign Origin", alice, aliceToken, "https://evil.example"),
        };
        System.out.printf("%-30s %-13s %s%n", "POST /transfer", "noncompliant", "compliant");
        for (Req r : requests) {
            System.out.printf("%-30s %-13s %s%n", r.label(),
                    checkNoncompliant(r.session(), r.token()) ? "accepted" : "rejected",
                    checkCompliant(r.session(), r.token(), r.origin()) ? "accepted" : "rejected");
        }
    }

    static String issueToken(String session) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        TOKENS_BY_SESSION.put(session, token);
        return token;
    }

    // Noncompliant: only checks a token if one was sent, and accepts any issued token
    static boolean checkNoncompliant(String session, String token) {
        if (token == null) {
            return true; // "older clients don't send it"
        }
        return TOKENS_BY_SESSION.containsValue(token); // not bound to the session; equals() inside
    }

    // Compliant: token required, bound to this session, constant-time comparison, Origin checked
    static boolean checkCompliant(String session, String token, String origin) {
        String expected = TOKENS_BY_SESSION.get(session);
        if (expected == null || token == null) {
            return false;
        }
        boolean tokenOk = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII), token.getBytes(StandardCharsets.US_ASCII));
        boolean originOk = origin != null && TRUSTED_ORIGINS.contains(origin);
        return tokenOk && originOk;
    }
}

/* Output:
token: 43 URL-safe chars from 32 random bytes; distinct per session: true
POST /transfer                 noncompliant  compliant
form on bank.example           accepted      accepted
forged form, no token          accepted      rejected
forged, attacker's own token   accepted      rejected
forged, guessed token          rejected      rejected
right token, foreign Origin    accepted      rejected
*/
