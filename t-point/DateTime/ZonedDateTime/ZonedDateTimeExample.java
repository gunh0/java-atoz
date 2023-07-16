// ZonedDateTime is a LocalDateTime plus a ZoneId (Europe/Berlin), so it pins
// down one exact instant AND remembers the region's rules: daylight saving
// time (DST) transitions, historical offset changes and so on.
// OffsetDateTime only carries a fixed offset (+02:00) without those rules.
//  - withZoneSameInstant: same moment, other region's wall clock
//  - withZoneSameLocal:   same wall clock, other moment
//  - in a spring-forward gap the local time does not exist and is shifted;
//    in an autumn overlap it exists twice and the earlier offset is chosen
// Store and compare instants (UTC); convert to a zone only for display.
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class ZonedDateTimeExample {
    public static void main(String[] args) {
        ZoneId seoul = ZoneId.of("Asia/Seoul");
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        ZoneId newYork = ZoneId.of("America/New_York");

        // An incident reported at 09:00 Seoul time
        ZonedDateTime incident = ZonedDateTime.of(LocalDateTime.of(2023, 7, 16, 9, 0), seoul);
        System.out.println("Seoul:    " + incident);
        System.out.println("Berlin:   " + incident.withZoneSameInstant(berlin));
        System.out.println("New York: " + incident.withZoneSameInstant(newYork));
        System.out.println("UTC:      " + incident.withZoneSameInstant(ZoneOffset.UTC));
        System.out.println("same instant? " + incident.toInstant().equals(incident.withZoneSameInstant(berlin).toInstant()));

        // Same local time, different instant
        ZonedDateTime nineInBerlin = incident.withZoneSameLocal(berlin);
        System.out.println("09:00 in Berlin is " + Duration.between(incident, nineInBerlin).toHours() + " hours later");

        // DST spring-forward gap: 02:30 on 2023-03-26 does not exist in Berlin
        ZonedDateTime gap = ZonedDateTime.of(LocalDateTime.of(2023, 3, 26, 2, 30), berlin);
        System.out.println("02:30 in the gap becomes " + gap);

        // DST autumn overlap: 02:30 on 2023-10-29 happens twice
        ZonedDateTime overlap = ZonedDateTime.of(LocalDateTime.of(2023, 10, 29, 2, 30), berlin);
        System.out.println("overlap, earlier: " + overlap);
        System.out.println("overlap, later:   " + overlap.withLaterOffsetAtOverlap());

        // A "day" across a DST change is not 24 hours
        ZonedDateTime before = ZonedDateTime.of(LocalDateTime.of(2023, 3, 25, 12, 0), berlin);
        System.out.println("plusDays(1) = " + before.plusDays(1) + " -> " + Duration.between(before, before.plusDays(1)).toHours() + " hours");
        System.out.println("plusHours(24) = " + before.plusHours(24));

        // OffsetDateTime: a fixed offset, as found in RFC 3339 timestamps in logs
        OffsetDateTime logged = OffsetDateTime.parse("2023-07-16T00:00:00+02:00");
        System.out.println("log entry in UTC: " + logged.withOffsetSameInstant(ZoneOffset.UTC));
        System.out.println("log entry in Seoul: " + logged.atZoneSameInstant(seoul));
    }
}

/* Output:
Seoul:    2023-07-16T09:00+09:00[Asia/Seoul]
Berlin:   2023-07-16T02:00+02:00[Europe/Berlin]
New York: 2023-07-15T20:00-04:00[America/New_York]
UTC:      2023-07-16T00:00Z
same instant? true
09:00 in Berlin is 7 hours later
02:30 in the gap becomes 2023-03-26T03:30+02:00[Europe/Berlin]
overlap, earlier: 2023-10-29T02:30+02:00[Europe/Berlin]
overlap, later:   2023-10-29T02:30+01:00[Europe/Berlin]
plusDays(1) = 2023-03-26T12:00+02:00[Europe/Berlin] -> 23 hours
plusHours(24) = 2023-03-26T13:00+02:00[Europe/Berlin]
log entry in UTC: 2023-07-15T22:00Z
log entry in Seoul: 2023-07-16T07:00+09:00[Asia/Seoul]
*/
