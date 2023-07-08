// LocalTime is a wall-clock time (09:30) and LocalDateTime is a date plus a
// time (2023-07-08T09:30), both still WITHOUT a time zone. They answer "what
// does the clock on the wall say", not "which instant on the timeline".
//  - LocalTime wraps around midnight: 23:30 plus one hour is 00:30
//  - truncatedTo(ChronoUnit.X) drops the smaller fields
//  - atDate/atTime combine the parts; toLocalDate/toLocalTime split them
// Good for opening hours or a maintenance window in a known office; for events
// that happen at one moment worldwide use Instant or ZonedDateTime instead.
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

public class LocalTimeExample {
    // A nightly maintenance window that crosses midnight
    static final LocalTime WINDOW_START = LocalTime.of(23, 0);
    static final LocalTime WINDOW_END = LocalTime.of(2, 0);

    static boolean inMaintenanceWindow(LocalTime t) {
        // The window wraps around midnight, so it is the union of two ranges
        return !t.isBefore(WINDOW_START) || t.isBefore(WINDOW_END);
    }

    public static void main(String[] args) {
        LocalTime t = LocalTime.of(9, 30, 15, 123_000_000);
        System.out.println("time: " + t);
        System.out.println("hour=" + t.getHour() + " minute=" + t.getMinute() + " second=" + t.getSecond());
        System.out.println("truncated to minutes: " + t.truncatedTo(ChronoUnit.MINUTES));
        System.out.println("23:30 plus 1 hour: " + LocalTime.of(23, 30).plusHours(1)); // wraps
        System.out.println("MIDNIGHT=" + LocalTime.MIDNIGHT + " NOON=" + LocalTime.NOON + " MAX=" + LocalTime.MAX);

        for (String s : new String[] {"22:59", "23:00", "01:59", "02:00", "12:00"}) {
            LocalTime time = LocalTime.parse(s);
            System.out.println("  " + s + " in maintenance window? " + inMaintenanceWindow(time));
        }

        // LocalDateTime = LocalDate + LocalTime
        LocalDateTime deploy = LocalDate.of(2023, 7, 8).atTime(23, 45);
        System.out.println("deploy at: " + deploy);
        LocalDateTime finished = deploy.plusMinutes(40); // crosses into the next day
        System.out.println("finished:  " + finished + " (date part " + finished.toLocalDate() + ", time part " + finished.toLocalTime() + ")");
        System.out.println("minutes between: " + ChronoUnit.MINUTES.between(deploy, finished));

        // Ordering and parsing
        LocalDateTime a = LocalDateTime.parse("2023-07-08T08:00:00");
        LocalDateTime b = LocalDateTime.parse("2023-07-08T08:00:01");
        System.out.println(a + " before " + b + "? " + a.isBefore(b));
        System.out.println("start of day: " + LocalDate.of(2023, 7, 8).atStartOfDay());
    }
}

/* Output:
time: 09:30:15.123
hour=9 minute=30 second=15
truncated to minutes: 09:30
23:30 plus 1 hour: 00:30
MIDNIGHT=00:00 NOON=12:00 MAX=23:59:59.999999999
  22:59 in maintenance window? false
  23:00 in maintenance window? true
  01:59 in maintenance window? true
  02:00 in maintenance window? false
  12:00 in maintenance window? false
deploy at: 2023-07-08T23:45
finished:  2023-07-09T00:25 (date part 2023-07-09, time part 00:25)
minutes between: 40
2023-07-08T08:00 before 2023-07-08T08:00:01? true
start of day: 2023-07-08T00:00
*/
