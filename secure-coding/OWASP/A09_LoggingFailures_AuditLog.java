// OWASP Top 10 2021: A09:2021 Security Logging and Monitoring Failures (CWE-778, CWE-117, CWE-532)
// https://owasp.org/Top10/A09_2021-Security_Logging_and_Monitoring_Failures/
//
// Breaches are usually found late because the evidence was never written:
// the application logs "user logged in" but not failed logins, denied
// access or privilege changes; what it does log is free text no alerting
// rule can match; secrets end up in the log; and an intruder who reaches
// the log file can delete the lines that describe them. An audit trail
// needs: an event for every security decision (success AND failure), a
// fixed structure (time, actor, action, target, outcome, source), secrets
// redacted before the event is built, alerting on the stream, and tamper
// evidence. Here each entry carries the SHA-256 of the previous entry (a
// hash chain), so removing or editing a line breaks verification from that
// point on. (Ship the log off the host too: a chain proves tampering, it
// does not prevent it.) The clock is fixed, so the hashes are stable.
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

public class A09_LoggingFailures_AuditLog {
    enum Outcome { SUCCESS, FAILURE, DENIED }

    record Event(String actor, String action, String target, Outcome outcome, String source, Map<String, String> details) { }

    // Noncompliant: free text, successes only, secrets included
    static final class TextLog {
        final List<String> lines = new ArrayList<>();

        void record(Event e) {
            if (e.outcome() == Outcome.SUCCESS) {
                lines.add(e.actor() + " did " + e.action() + " " + new TreeMap<>(e.details()));
            }
        }
    }

    // Compliant: structured, every outcome, redacted, hash-chained
    static final class AuditLog {
        static final Pattern SECRET_KEYS = Pattern.compile("(?i).*(password|token|secret|authorization|cookie).*");
        static final String GENESIS = "0".repeat(64);
        Clock clock;
        final List<String> lines = new ArrayList<>();
        private String lastHash = GENESIS;

        AuditLog(Clock clock) {
            this.clock = clock;
        }

        void record(Event e) {
            Map<String, String> safe = new TreeMap<>();
            e.details().forEach((k, v) -> safe.put(k, SECRET_KEYS.matcher(k).matches() ? "[REDACTED]" : v));
            String body = "{\"ts\":\"" + clock.instant() + "\",\"actor\":" + q(e.actor()) + ",\"action\":" + q(e.action())
                    + ",\"target\":" + q(e.target()) + ",\"outcome\":\"" + e.outcome() + "\",\"src\":" + q(e.source())
                    + ",\"details\":" + json(safe) + ",\"prev\":\"" + lastHash + "\"}";
            lastHash = sha256(body);
            lines.add(body);
        }

        // Recompute the chain; returns the index of the first entry that does not link, or -1
        static int verify(List<String> lines) {
            String prev = GENESIS;
            for (int i = 0; i < lines.size(); i++) {
                if (!lines.get(i).endsWith(",\"prev\":\"" + prev + "\"}")) return i;
                prev = sha256(lines.get(i));
            }
            return -1;
        }

        static String q(String s) {
            StringBuilder b = new StringBuilder("\"");
            for (char c : s.toCharArray()) {
                if (c == '"' || c == '\\') b.append('\\').append(c);
                else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                else b.append(c);
            }
            return b.append('"').toString();
        }

        static String json(Map<String, String> m) {
            List<String> parts = new ArrayList<>();
            m.forEach((k, v) -> parts.add(q(k) + ":" + q(v)));
            return "{" + String.join(",", parts) + "}";
        }
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void main(String[] args) {
        List<Event> events = List.of(
                new Event("mallory", "login", "mallory", Outcome.FAILURE, "203.0.113.7", Map.of("password", "letmein")),
                new Event("mallory", "login", "mallory", Outcome.FAILURE, "203.0.113.7", Map.of("password", "123456")),
                new Event("mallory", "login", "mallory", Outcome.SUCCESS, "203.0.113.7", Map.of("password", "Summer2025")),
                new Event("mallory", "export", "customers.csv", Outcome.DENIED, "203.0.113.7", Map.of("rows", "50000")),
                new Event("mallory", "role.grant", "mallory", Outcome.SUCCESS, "203.0.113.7",
                        Map.of("role", "admin", "authToken", "eyJhbGciOi.demo")));

        TextLog text = new TextLog();
        events.forEach(text::record);
        System.out.println("noncompliant log (" + text.lines.size() + " of " + events.size() + " events):");
        text.lines.forEach(l -> System.out.println("  " + l));

        // A test clock: fixed start, one minute per event
        Clock start = Clock.fixed(Instant.parse("2025-06-29T08:00:00Z"), ZoneOffset.UTC);
        AuditLog audit = new AuditLog(start);
        for (Event e : events) {
            audit.record(e);
            audit.clock = Clock.offset(audit.clock, Duration.ofMinutes(1));
        }
        List<String> written = audit.lines;
        System.out.println("compliant audit log (" + written.size() + " of " + events.size() + " events):");
        written.forEach(l -> System.out.println("  " + l.replaceAll("\"prev\":\"(\\w{12})\\w+\"", "\"prev\":\"$1...\"")));

        // Monitoring: a simple rule over the structured stream
        long failures = written.stream().filter(l -> l.contains("\"action\":\"login\"") && l.contains("\"FAILURE\"")).count();
        boolean escalation = written.stream().anyMatch(l -> l.contains("\"action\":\"role.grant\""));
        System.out.println("alert: " + failures + " failed logins then a role grant from one source -> " + (failures >= 2 && escalation));

        System.out.println("chain verifies (first broken entry, -1 = intact): " + AuditLog.verify(written));
        List<String> deleted = new ArrayList<>(written);
        deleted.remove(3); // the intruder removes the DENIED export attempt
        System.out.println("after deleting entry 3: " + AuditLog.verify(deleted) + " (the entry now at 3 links to the deleted one)");
        List<String> edited = new ArrayList<>(written);
        edited.set(1, edited.get(1).replace("FAILURE", "SUCCESS"));
        System.out.println("after editing entry 1:  " + AuditLog.verify(edited) + " (entry 2 no longer links to it)");
    }
}

/* Output:
noncompliant log (2 of 5 events):
  mallory did login {password=Summer2025}
  mallory did role.grant {authToken=eyJhbGciOi.demo, role=admin}
compliant audit log (5 of 5 events):
  {"ts":"2025-06-29T08:00:00Z","actor":"mallory","action":"login","target":"mallory","outcome":"FAILURE","src":"203.0.113.7","details":{"password":"[REDACTED]"},"prev":"000000000000..."}
  {"ts":"2025-06-29T08:01:00Z","actor":"mallory","action":"login","target":"mallory","outcome":"FAILURE","src":"203.0.113.7","details":{"password":"[REDACTED]"},"prev":"ec63ef340e49..."}
  {"ts":"2025-06-29T08:02:00Z","actor":"mallory","action":"login","target":"mallory","outcome":"SUCCESS","src":"203.0.113.7","details":{"password":"[REDACTED]"},"prev":"b200cadb75e4..."}
  {"ts":"2025-06-29T08:03:00Z","actor":"mallory","action":"export","target":"customers.csv","outcome":"DENIED","src":"203.0.113.7","details":{"rows":"50000"},"prev":"cf5f002a2415..."}
  {"ts":"2025-06-29T08:04:00Z","actor":"mallory","action":"role.grant","target":"mallory","outcome":"SUCCESS","src":"203.0.113.7","details":{"authToken":"[REDACTED]","role":"admin"},"prev":"1c03f790428e..."}
alert: 2 failed logins then a role grant from one source -> true
chain verifies (first broken entry, -1 = intact): -1
after deleting entry 3: 3 (the entry now at 3 links to the deleted one)
after editing entry 1:  2 (entry 2 no longer links to it)
*/
