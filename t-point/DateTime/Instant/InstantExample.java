// Instant is a point on the UTC timeline: seconds + nanoseconds since the
// epoch 1970-01-01T00:00:00Z. It has no time zone and no calendar fields, which
// makes it the right type for timestamps in logs, tokens and databases.
// Clock is the source of "now". Code that calls Instant.now(clock) instead of
// Instant.now() can be tested with Clock.fixed or Clock.offset, so expiry
// logic (sessions, JWT "exp", one-time codes) is deterministic in tests.
//  - toEpochMilli / getEpochSecond convert to Unix time
//  - plus/minus take a Duration; isBefore/isAfter compare
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public class InstantExample {
    // A session that expires after a fixed idle time, using an injected Clock
    static final class Session {
        private final Clock clock;
        private final Duration timeout;
        private Instant lastSeen;

        Session(Clock clock, Duration timeout) {
            this.clock = clock;
            this.timeout = timeout;
            this.lastSeen = Instant.now(clock);
        }

        void touch() { lastSeen = Instant.now(clock); }
        boolean expired() { return Instant.now(clock).isAfter(lastSeen.plus(timeout)); }
    }

    public static void main(String[] args) {
        Instant epoch = Instant.EPOCH;
        Instant t = Instant.parse("2023-07-21T10:15:30.500Z");
        System.out.println("epoch: " + epoch);
        System.out.println("t: " + t + ", epochSecond=" + t.getEpochSecond() + ", nano=" + t.getNano() + ", millis=" + t.toEpochMilli());
        System.out.println("from Unix time 1700000000: " + Instant.ofEpochSecond(1_700_000_000L));
        System.out.println("t truncated to hours: " + t.truncatedTo(ChronoUnit.HOURS));
        System.out.println("t at Seoul: " + t.atZone(ZoneId.of("Asia/Seoul")));

        // A JWT-style expiry check: exp is in seconds since the epoch
        long exp = t.plus(Duration.ofMinutes(15)).getEpochSecond();
        for (Instant now : new Instant[] {t.plusSeconds(60), t.plusSeconds(900), t.plusSeconds(901)}) {
            boolean valid = now.getEpochSecond() <= exp;
            System.out.println("token at " + now + " valid? " + valid);
        }

        // Deterministic time with a fixed clock that we move by hand
        MutableClock clock = new MutableClock(t);
        Session session = new Session(clock, Duration.ofMinutes(30));
        clock.advance(Duration.ofMinutes(20));
        System.out.println("after 20 min idle, expired? " + session.expired());
        session.touch();
        clock.advance(Duration.ofMinutes(29));
        System.out.println("touched, then 29 min idle, expired? " + session.expired());
        clock.advance(Duration.ofMinutes(2));
        System.out.println("then 2 more minutes, expired? " + session.expired());

        // The built-in test clocks
        Clock fixed = Clock.fixed(t, ZoneOffset.UTC);
        Clock later = Clock.offset(fixed, Duration.ofHours(1));
        System.out.println("fixed=" + Instant.now(fixed) + " offset=" + Instant.now(later));
    }

    // A Clock whose instant the test controls
    static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant start) { now = start; }
        void advance(Duration d) { now = now.plus(d); }
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { throw new UnsupportedOperationException(); }
    }
}

/* Output:
epoch: 1970-01-01T00:00:00Z
t: 2023-07-21T10:15:30.500Z, epochSecond=1689934530, nano=500000000, millis=1689934530500
from Unix time 1700000000: 2023-11-14T22:13:20Z
t truncated to hours: 2023-07-21T10:00:00Z
t at Seoul: 2023-07-21T19:15:30.500+09:00[Asia/Seoul]
token at 2023-07-21T10:16:30.500Z valid? true
token at 2023-07-21T10:30:30.500Z valid? true
token at 2023-07-21T10:30:31.500Z valid? false
after 20 min idle, expired? false
touched, then 29 min idle, expired? false
then 2 more minutes, expired? true
fixed=2023-07-21T10:15:30.500Z offset=2023-07-21T11:15:30.500Z
*/
