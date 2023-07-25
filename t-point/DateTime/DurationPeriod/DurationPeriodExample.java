// Two kinds of amount of time:
//  - Duration: an exact amount of seconds and nanos (PT1H30M). Use it with
//    Instant and LocalTime, for timeouts, rate limits, elapsed times.
//  - Period: years, months and days in calendar terms (P1Y2M3D). Use it with
//    LocalDate, for "one month later" or ages, where months differ in length.
// Both print and parse the ISO-8601 forms. "One day" differs: Duration.ofDays(1)
// is always 24 h, Period.ofDays(1) is one calendar day (23 or 25 h across DST).
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class DurationPeriodExample {
    public static void main(String[] args) {
        // Duration
        Duration timeout = Duration.ofMinutes(90);
        System.out.println("timeout: " + timeout + " = " + timeout.toSeconds() + " s = " + timeout.toHours() + " h " + timeout.toMinutesPart() + " min");
        Duration elapsed = Duration.between(LocalTime.of(9, 15), LocalTime.of(11, 2, 30));
        System.out.println("elapsed: " + elapsed);
        System.out.println("parse PT2H30M: " + Duration.parse("PT2H30M").toMinutes() + " minutes");
        System.out.println("elapsed > timeout? " + (elapsed.compareTo(timeout) > 0));

        // Rate limiting: how many requests fit in a window
        Duration window = Duration.ofSeconds(60);
        Duration perRequest = Duration.ofMillis(250);
        System.out.println("requests per window: " + window.dividedBy(perRequest));

        // Period
        Period retention = Period.of(1, 6, 0);
        System.out.println("retention: " + retention + " (" + retention.toTotalMonths() + " months)");
        LocalDate created = LocalDate.of(2022, 1, 31);
        System.out.println("log created " + created + ", delete after " + created.plus(retention));

        Period age = Period.between(LocalDate.of(1990, 8, 15), LocalDate.of(2023, 7, 25));
        System.out.println("age: " + age.getYears() + " years, " + age.getMonths() + " months, " + age.getDays() + " days");
        System.out.println("normalized P14M: " + Period.ofMonths(14).normalized());

        // The DST difference between a calendar day and 24 hours
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        ZonedDateTime start = ZonedDateTime.of(LocalDateTime.of(2023, 3, 25, 12, 0), berlin);
        System.out.println("start:             " + start);
        System.out.println("+ Period.ofDays(1):   " + start.plus(Period.ofDays(1)));
        System.out.println("+ Duration.ofDays(1): " + start.plus(Duration.ofDays(1)));
    }
}

/* Output:
timeout: PT1H30M = 5400 s = 1 h 30 min
elapsed: PT1H47M30S
parse PT2H30M: 150 minutes
elapsed > timeout? true
requests per window: 240
retention: P1Y6M (18 months)
log created 2022-01-31, delete after 2023-07-31
age: 32 years, 11 months, 10 days
normalized P14M: P1Y2M
start:             2023-03-25T12:00+01:00[Europe/Berlin]
+ Period.ofDays(1):   2023-03-26T12:00+02:00[Europe/Berlin]
+ Duration.ofDays(1): 2023-03-26T13:00+02:00[Europe/Berlin]
*/
