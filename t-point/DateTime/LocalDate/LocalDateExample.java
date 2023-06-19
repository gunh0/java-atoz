// LocalDate is a date without a time or a time zone: 2023-06-19, nothing more.
// It is the right type for birthdays, expiry dates and "valid until" fields.
//  - immutable: plusDays/withMonth/... return a new object, the old one is unchanged
//  - LocalDate.of(...) validates (Feb 30 throws DateTimeException)
//  - isBefore/isAfter/isEqual compare, and ChronoUnit.DAYS.between counts days
//  - leap years, month lengths and day-of-week are all handled for you
// Avoid LocalDate.now() in code you want to test; pass the date (or a Clock) in.
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class LocalDateExample {
    record Certificate(String host, LocalDate notAfter) {}

    public static void main(String[] args) {
        LocalDate today = LocalDate.of(2023, Month.JUNE, 19);
        System.out.println("today: " + today + " (" + today.getDayOfWeek() + ", day " + today.getDayOfYear() + " of the year)");

        // Immutability: plus* returns a new date
        LocalDate inThirtyDays = today.plusDays(30);
        System.out.println("plusDays(30): " + inThirtyDays + ", today is still " + today);
        System.out.println("plusMonths(1) from Jan 31: " + LocalDate.of(2023, 1, 31).plusMonths(1)); // clamps to month end

        // Leap years and month lengths
        System.out.println("2023 leap year? " + today.isLeapYear() + ", 2024 leap year? " + LocalDate.of(2024, 1, 1).isLeapYear());
        System.out.println("days in February 2024: " + LocalDate.of(2024, 2, 1).lengthOfMonth());

        // Validation happens at construction
        try {
            LocalDate.of(2023, 2, 30);
        } catch (DateTimeException e) {
            System.out.println("of(2023, 2, 30) -> " + e.getMessage());
        }

        // Parsing the ISO-8601 form (yyyy-MM-dd) needs no formatter
        LocalDate parsed = LocalDate.parse("2023-12-31");
        System.out.println("parsed: " + parsed + ", year end? " + (parsed.getDayOfYear() == parsed.lengthOfYear()));

        // A realistic check: which TLS certificates expire within 30 days?
        List<Certificate> certs = List.of(
                new Certificate("api.example.com", LocalDate.of(2023, 7, 1)),
                new Certificate("vpn.example.com", LocalDate.of(2023, 12, 1)),
                new Certificate("old.example.com", LocalDate.of(2023, 6, 1)));
        for (Certificate c : certs) {
            long days = ChronoUnit.DAYS.between(today, c.notAfter());
            String status = c.notAfter().isBefore(today) ? "EXPIRED"
                    : days <= 30 ? "renew soon" : "ok";
            System.out.printf("%-16s notAfter=%s days=%4d %s%n", c.host(), c.notAfter(), days, status);
        }

        // Day-of-week arithmetic: the next Monday after today
        LocalDate next = today.plusDays(1);
        while (next.getDayOfWeek() != DayOfWeek.MONDAY) next = next.plusDays(1);
        System.out.println("next Monday after " + today + ": " + next);

        // Dates iterate naturally with datesUntil (end exclusive)
        System.out.println("this week: " + today.datesUntil(today.plusDays(5)).map(d -> d.getDayOfWeek().toString().substring(0, 3)).toList());
    }
}

/* Output:
today: 2023-06-19 (MONDAY, day 170 of the year)
plusDays(30): 2023-07-19, today is still 2023-06-19
plusMonths(1) from Jan 31: 2023-02-28
2023 leap year? false, 2024 leap year? true
days in February 2024: 29
of(2023, 2, 30) -> Invalid date 'FEBRUARY 30'
parsed: 2023-12-31, year end? true
api.example.com  notAfter=2023-07-01 days=  12 renew soon
vpn.example.com  notAfter=2023-12-01 days= 165 ok
old.example.com  notAfter=2023-06-01 days= -18 EXPIRED
next Monday after 2023-06-19: 2023-06-26
this week: [MON, TUE, WED, THU, FRI]
*/
