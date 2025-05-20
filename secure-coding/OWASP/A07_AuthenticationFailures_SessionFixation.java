// OWASP Top 10 2021: A07:2021 Identification and Authentication Failures, session fixation (CWE-384)
// https://owasp.org/Top10/A07_2021-Identification_and_Authentication_Failures/
//
// A session id is a bearer credential. If the server keeps using the id the
// browser presented BEFORE login, an attacker who can plant an id in the
// victim's browser (a link with ;jsessionid=..., a cookie set from a
// sibling subdomain, a shared kiosk) simply waits: once the victim logs in,
// the attacker's copy of that id is an authenticated session. Issue a fresh,
// unpredictable id at every privilege change (login, step-up, role switch),
// invalidate the old one, and never accept ids the server did not create.
// In servlet containers this is HttpServletRequest.changeSessionId(); the
// in-memory store below shows what that call has to do. Ids are never
// printed, only comparisons.
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class A07_AuthenticationFailures_SessionFixation {
    static final class Session {
        String user; // null = anonymous
        final Map<String, String> attributes = new HashMap<>();
    }

    static final class SessionStore {
        private static final SecureRandom RANDOM = new SecureRandom();
        private final Map<String, Session> sessions = new HashMap<>();
        private final boolean acceptClientIds;

        SessionStore(boolean acceptClientIds) {
            this.acceptClientIds = acceptClientIds;
        }

        static String newId() {
            byte[] b = new byte[32]; // 256 bits from a CSPRNG
            RANDOM.nextBytes(b);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        }

        // Returns the id the response sets as the session cookie
        String getOrCreate(String presentedId) {
            if (presentedId != null && sessions.containsKey(presentedId)) return presentedId;
            String id = (presentedId != null && acceptClientIds) ? presentedId : newId();
            sessions.put(id, new Session());
            return id;
        }

        Optional<String> userOf(String id) {
            return Optional.ofNullable(sessions.get(id)).map(s -> s.user);
        }

        // Noncompliant: mark the existing session as authenticated, id unchanged
        String loginNoncompliant(String id, String user) {
            sessions.get(id).user = user;
            return id;
        }

        // Compliant: move state to a NEW id, destroy the old one
        String loginCompliant(String id, String user) {
            Session old = sessions.remove(id);
            Session fresh = new Session();
            fresh.attributes.putAll(old.attributes); // e.g. the shopping cart survives
            fresh.user = user;
            String newId = newId();
            sessions.put(newId, fresh);
            return newId;
        }
    }

    static void scenario(String label, boolean compliant) {
        SessionStore store = new SessionStore(!compliant);

        // 1. The attacker gets an anonymous session from the site (or makes one up)
        String planted = compliant ? store.getOrCreate(null) : store.getOrCreate("attacker-chosen-id");

        // 2. The victim's browser is made to present that id, puts something in the cart, logs in
        String victimId = store.getOrCreate(planted);
        store.sessions.get(victimId).attributes.put("cart", "1 x book");
        String afterLogin = compliant ? store.loginCompliant(victimId, "alice") : store.loginNoncompliant(victimId, "alice");

        // 3. The attacker replays the planted id
        System.out.println(label);
        System.out.println("  victim's id changed at login:   " + !afterLogin.equals(planted));
        System.out.println("  victim logged in as:            " + store.userOf(afterLogin).orElse("(anonymous)"));
        System.out.println("  victim's cart kept:             " + store.sessions.get(afterLogin).attributes.containsKey("cart"));
        System.out.println("  attacker's planted id works:    " + store.sessions.containsKey(planted));
        System.out.println("  attacker is logged in as:       " + store.userOf(planted).orElse("(nobody)"));
    }

    public static void main(String[] args) {
        scenario("noncompliant (keep the pre-login id, accept client ids):", false);
        scenario("compliant (server-issued ids, rotate on login):", true);
    }
}

/* Output:
noncompliant (keep the pre-login id, accept client ids):
  victim's id changed at login:   false
  victim logged in as:            alice
  victim's cart kept:             true
  attacker's planted id works:    true
  attacker is logged in as:       alice
compliant (server-issued ids, rotate on login):
  victim's id changed at login:   true
  victim logged in as:            alice
  victim's cart kept:             true
  attacker's planted id works:    false
  attacker is logged in as:       (nobody)
*/
