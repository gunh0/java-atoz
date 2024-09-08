// FIO13-J. Do not log sensitive information outside a trust boundary
// https://wiki.sei.cmu.edu/confluence/display/java/FIO13-J.+Do+not+log+sensitive+information+outside+a+trust+boundary
//
// Logs travel further than the data they describe: to log files readable
// by operators, to aggregation services, to support tickets and backups.
// A log line that contains a password, a session token, a full card number
// or an API key turns every one of those places into a copy of the secret.
// Decide what may be logged, not what must be hidden: log identifiers and
// outcomes, mask or omit secrets, and make the safe thing the default by
// giving sensitive types a toString() that redacts them.
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class FIO13_SensitiveInfoInLogs {
    static final List<String> LOG = new ArrayList<>();

    public static void main(String[] args) {
        Logger log = Logger.getLogger("fio13");
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord r) {
                LOG.add(r.getLevel() + " " + new SimpleFormatter().formatMessage(r));
            }

            @Override public void flush() {}
            @Override public void close() {}
        });

        Map<String, String> request = new TreeMap<>(Map.of(
                "user", "alice",
                "password", "correct horse battery staple",
                "card", "4111111111111111",
                "Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.e30.sig"));

        // Noncompliant: dump the whole request
        log.log(Level.INFO, "login request {0}", request);
        log.log(Level.INFO, "charging card {0}", request.get("card"));

        // Compliant: log only what is needed, masked
        log.log(Level.INFO, "login request {0}", redact(request));
        log.log(Level.INFO, "charging card {0}", maskCard(request.get("card")));
        log.log(Level.INFO, "login for {0} with {1}", new Object[]{"alice", new Secret("correct horse battery staple")});

        System.out.println("noncompliant:");
        LOG.subList(0, 2).forEach(l -> System.out.println("  " + l));
        System.out.println("compliant:");
        LOG.subList(2, 5).forEach(l -> System.out.println("  " + l));
    }

    static final List<String> SENSITIVE = List.of("password", "card", "authorization", "token", "secret");

    static Map<String, String> redact(Map<String, String> fields) {
        Map<String, String> copy = new TreeMap<>();
        fields.forEach((k, v) -> copy.put(k, SENSITIVE.contains(k.toLowerCase()) ? "[REDACTED]" : v));
        return copy;
    }

    static String maskCard(String pan) {
        return "**** " + pan.substring(pan.length() - 4);
    }
}

// A type that cannot be logged by accident
record Secret(String value) {
    @Override
    public String toString() {
        return "Secret[***]";
    }
}

/* Output:
noncompliant:
  INFO login request {Authorization=Bearer eyJhbGciOiJIUzI1NiJ9.e30.sig, card=4111111111111111, password=correct horse battery staple, user=alice}
  INFO charging card 4111111111111111
compliant:
  INFO login request {Authorization=[REDACTED], card=[REDACTED], password=[REDACTED], user=alice}
  INFO charging card **** 1111
  INFO login for alice with Secret[***]
*/
