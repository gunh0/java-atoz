// OWASP Top 10 2021: A07:2021 Identification and Authentication Failures, brute force (CWE-307, CWE-204)
// https://owasp.org/Top10/A07_2021-Identification_and_Authentication_Failures/
//
// A login endpoint that answers every attempt at full speed lets an
// attacker walk a password list against one account, or "spray" one common
// password across thousands of accounts, and different messages for
// "unknown user" and "wrong password" tell them which accounts exist.
// Throttle on two keys: per account (exponential backoff after a few
// failures, temporary rather than permanent, so attackers cannot lock real
// users out forever) and per source (a sliding window of failures per
// client address, which catches spraying that stays under every per-account
// limit). Return one uniform answer for every failure, including "locked".
// Time comes from an injectable clock, so the demo replays an hour of
// attacks in milliseconds and its output is deterministic.
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class A07_AuthenticationFailures_BruteForceThrottling {
    static final class FakeClock {
        Instant now = Instant.parse("2025-05-30T09:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }
    }

    interface LoginService {
        String login(String user, String password, String sourceIp);
    }

    static final Map<String, String> USERS = new HashMap<>(); // demo only: real code stores password hashes

    // Noncompliant: unlimited attempts and revealing messages
    static final class NaiveLogin implements LoginService {
        public String login(String user, String password, String sourceIp) {
            String expected = USERS.get(user);
            if (expected == null) return "no such user";
            return expected.equals(password) ? "ok" : "wrong password";
        }
    }

    // Compliant: per-account backoff + per-source window + uniform failure message
    static final class ThrottledLogin implements LoginService {
        static final int FREE_FAILURES = 5;
        static final Duration BASE_DELAY = Duration.ofMinutes(1), MAX_DELAY = Duration.ofMinutes(30);
        static final int SOURCE_LIMIT = 20;
        static final Duration SOURCE_WINDOW = Duration.ofMinutes(10);
        static final String FAILED = "invalid username or password";

        private final FakeClock clock;
        private final Map<String, Integer> failures = new HashMap<>();
        private final Map<String, Instant> lockedUntil = new HashMap<>();
        private final Map<String, Deque<Instant>> sourceFailures = new HashMap<>();
        int passwordChecks;

        ThrottledLogin(FakeClock clock) {
            this.clock = clock;
        }

        public String login(String user, String password, String sourceIp) {
            Instant now = clock.now;
            Deque<Instant> recent = sourceFailures.computeIfAbsent(sourceIp, ip -> new ArrayDeque<>());
            while (!recent.isEmpty() && !recent.peekFirst().isAfter(now.minus(SOURCE_WINDOW))) recent.pollFirst();
            if (recent.size() >= SOURCE_LIMIT) return FAILED; // source throttled: do not even check
            if (now.isBefore(lockedUntil.getOrDefault(user, Instant.MIN))) return FAILED; // account backing off

            passwordChecks++;
            String expected = USERS.get(user); // (real code: hash a dummy password for unknown users, same timing)
            if (expected != null && expected.equals(password)) {
                failures.remove(user);
                return "ok";
            }
            recent.addLast(now);
            int n = failures.merge(user, 1, Integer::sum);
            if (n >= FREE_FAILURES) {
                // 1, 2, 4, 8, 16, 30, 30, ... minutes
                Duration delay = BASE_DELAY.multipliedBy(1L << Math.min(n - FREE_FAILURES, 10));
                lockedUntil.put(user, now.plus(delay.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : delay));
            }
            return FAILED;
        }
    }

    static List<String> passwordList() {
        List<String> list = new ArrayList<>();
        for (int i = 1; i <= 200; i++) list.add("guess-" + i);
        list.set(149, "Sunshine2025!"); // alice's password is the 150th entry of the attacker's list
        return list;
    }

    // The attacker's list against alice, one guess per second
    static String attackOneAccount(LoginService service, FakeClock clock) {
        int attempts = 0;
        for (String guess : passwordList()) {
            attempts++;
            if (service.login("alice", guess, "203.0.113.7").equals("ok")) {
                return "cracked after " + attempts + " attempts";
            }
            clock.advance(Duration.ofSeconds(1));
        }
        return "not cracked in " + attempts + " attempts";
    }

    static String spray(LoginService service, FakeClock clock) {
        for (int i = 0; i < 100; i++) {
            if (service.login("user" + i, "Winter2025!", "203.0.113.7").equals("ok")) return "hit user" + i;
            clock.advance(Duration.ofSeconds(1));
        }
        return "no hit";
    }

    public static void main(String[] args) {
        USERS.put("alice", "Sunshine2025!");
        for (int i = 0; i < 100; i++) USERS.put("user" + i, i == 42 ? "Winter2025!" : "pw-" + i);

        FakeClock c1 = new FakeClock();
        System.out.println("noncompliant, password list against alice: " + attackOneAccount(new NaiveLogin(), c1));
        NaiveLogin naive = new NaiveLogin();
        System.out.println("noncompliant, messages: " + new TreeSet<>(List.of(
                naive.login("alice", "x", "203.0.113.7"), naive.login("mallory", "x", "203.0.113.7")))
                + " -> accounts can be enumerated");

        FakeClock c2 = new FakeClock();
        ThrottledLogin throttled = new ThrottledLogin(c2);
        System.out.println("compliant, password list against alice:    " + attackOneAccount(throttled, c2)
                + ", passwords actually checked: " + throttled.passwordChecks);
        c2.advance(Duration.ofHours(1));
        System.out.println("compliant, alice logs in an hour later:     " + throttled.login("alice", "Sunshine2025!", "198.51.100.20"));

        // Password spraying: one common password, one attempt per account, all from one address
        System.out.println("noncompliant, spraying 100 accounts:        " + spray(new NaiveLogin(), new FakeClock()));
        FakeClock c3 = new FakeClock();
        ThrottledLogin sprayed = new ThrottledLogin(c3);
        System.out.println("compliant, spraying 100 accounts:           " + spray(sprayed, c3)
                + ", passwords actually checked: " + sprayed.passwordChecks);
        System.out.println("compliant, messages: " + new TreeSet<>(List.of(
                throttled.login("alice", "x", "192.0.2.1"), throttled.login("mallory", "x", "192.0.2.1"))));
    }
}

/* Output:
noncompliant, password list against alice: cracked after 150 attempts
noncompliant, messages: [no such user, wrong password] -> accounts can be enumerated
compliant, password list against alice:    not cracked in 200 attempts, passwords actually checked: 7
compliant, alice logs in an hour later:     ok
noncompliant, spraying 100 accounts:        hit user42
compliant, spraying 100 accounts:           no hit, passwords actually checked: 20
compliant, messages: [invalid username or password]
*/
