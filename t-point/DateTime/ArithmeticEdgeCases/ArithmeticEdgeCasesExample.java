// Date arithmetic is calendar arithmetic, and the calendar is irregular:
//  - plusMonths clamps to the last valid day (Jan 31 + 1 month = Feb 28), so
//    month steps are not reversible and not additive: compute recurring
//    dates from a fixed anchor (anchor.plusMonths(n)), not step by step
//  - a Period is applied years, then months, then days, so adding P1M1D is not
//    the same as adding 1 day and then 1 month
//  - ChronoUnit.MONTHS.between counts COMPLETE months; Period.between gives
//    years/months/days; Period.normalized() never turns days into months
//  - LocalDateTime knows nothing about time zones, so Duration.between on it
//    is wrong across a DST change; use ZonedDateTime or Instant
//  - a daily job derived from yesterday's ZonedDateTime keeps a gap-shifted
//    time forever; rebuild it from the LocalDate and LocalTime each day
//  - midnight may not exist: use LocalDate.atStartOfDay(zone), never
//    atTime(0, 0) or truncatedTo(DAYS) assumptions
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class ArithmeticEdgeCasesExample {
    public static void main(String[] args) {
        System.out.println("-- month ends");
        LocalDate jan31 = LocalDate.of(2025, 1, 31);
        List<LocalDate> stepwise = new ArrayList<>();
        List<LocalDate> anchored = new ArrayList<>();
        LocalDate d = jan31;
        for (int n = 1; n <= 4; n++) {
            d = d.plusMonths(1);
            stepwise.add(d);
            anchored.add(jan31.plusMonths(n));
        }
        System.out.println("billing step by step:  " + stepwise);
        System.out.println("billing from anchor:   " + anchored);
        LocalDate mar31 = LocalDate.of(2025, 3, 31);
        System.out.println("Mar 31 - 1 month + 1 month = " + mar31.minusMonths(1).plusMonths(1));

        System.out.println("-- leap days");
        LocalDate leap = LocalDate.of(2024, 2, 29);
        System.out.println("2024-02-29 plusYears(1) = " + leap.plusYears(1) + ", plusYears(4) = " + leap.plusYears(4));
        System.out.println("age on 2025-02-28: " + Period.between(leap, LocalDate.of(2025, 2, 28)).getYears()
                + ", on 2025-03-01: " + Period.between(leap, LocalDate.of(2025, 3, 1)).getYears());
        System.out.println("days in 2024 / 2025: " + LocalDate.of(2024, 1, 1).lengthOfYear() + " / " + LocalDate.of(2025, 1, 1).lengthOfYear());

        System.out.println("-- Period vs ChronoUnit");
        LocalDate from = LocalDate.of(2025, 1, 31);
        LocalDate to = LocalDate.of(2025, 2, 28);
        System.out.println("Jan 31 -> Feb 28: Period " + Period.between(from, to)
                + ", MONTHS.between " + ChronoUnit.MONTHS.between(from, to) + ", DAYS.between " + ChronoUnit.DAYS.between(from, to));
        System.out.println("Jan 31 -> Mar 1:  Period " + Period.between(from, LocalDate.of(2025, 3, 1)));
        System.out.println("P45D normalized: " + Period.ofDays(45).normalized() + ", P13M normalized: " + Period.ofMonths(13).normalized());
        LocalDate jan30 = LocalDate.of(2025, 1, 30);
        System.out.println("Jan 30 + P1M1D = " + jan30.plus(Period.of(0, 1, 1))
                + ", Jan 30 + 1 day + 1 month = " + jan30.plusDays(1).plusMonths(1));

        System.out.println("-- DST: night shift hours in Europe/Berlin");
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        for (LocalDate night : List.of(LocalDate.of(2025, 3, 29), LocalDate.of(2025, 6, 14), LocalDate.of(2025, 10, 25))) {
            LocalDateTime start = night.atTime(22, 0);
            LocalDateTime end = night.plusDays(1).atTime(6, 0);
            Duration naive = Duration.between(start, end);
            Duration real = Duration.between(start.atZone(berlin), end.atZone(berlin));
            System.out.println(night + " 22:00-06:00: LocalDateTime says " + naive.toHours() + " h, worked " + real.toHours() + " h");
        }
        ZonedDateTime sat = ZonedDateTime.of(2025, 3, 29, 12, 0, 0, 0, berlin);
        ZonedDateTime sun = sat.plusDays(1);
        System.out.println("Sat noon -> Sun noon: DAYS.between " + ChronoUnit.DAYS.between(sat, sun)
                + ", HOURS.between " + ChronoUnit.HOURS.between(sat, sun)
                + ", Duration.toDays " + Duration.between(sat, sun).toDays());

        System.out.println("-- a daily 02:30 job across the spring gap");
        LocalTime runAt = LocalTime.of(2, 30);
        ZonedDateTime derived = LocalDate.of(2025, 3, 30).atTime(runAt).atZone(berlin); // 02:30 does not exist that day
        for (int i = 0; i < 3; i++) {
            LocalDate day = LocalDate.of(2025, 3, 30).plusDays(i);
            ZonedDateTime rebuilt = day.atTime(runAt).atZone(berlin);
            System.out.println(day + ": derived " + derived.toLocalTime() + ", rebuilt " + rebuilt.toLocalTime());
            derived = derived.plusDays(1); // keeps 03:30 forever
        }

        System.out.println("-- days without a midnight (America/Santiago)");
        ZoneId santiago = ZoneId.of("America/Santiago");
        LocalDate dstStart = LocalDate.of(2024, 9, 8);
        System.out.println("atStartOfDay:        " + dstStart.atStartOfDay(santiago));
        System.out.println("atTime(0, 0).atZone: " + dstStart.atTime(0, 0).atZone(santiago) + " (same, resolved by shifting)");
        System.out.println("hours in that day:   " + Duration.between(dstStart.atStartOfDay(santiago),
                dstStart.plusDays(1).atStartOfDay(santiago)).toHours());
    }
}

/* Output:
-- month ends
billing step by step:  [2025-02-28, 2025-03-28, 2025-04-28, 2025-05-28]
billing from anchor:   [2025-02-28, 2025-03-31, 2025-04-30, 2025-05-31]
Mar 31 - 1 month + 1 month = 2025-03-28
-- leap days
2024-02-29 plusYears(1) = 2025-02-28, plusYears(4) = 2028-02-29
age on 2025-02-28: 0, on 2025-03-01: 1
days in 2024 / 2025: 366 / 365
-- Period vs ChronoUnit
Jan 31 -> Feb 28: Period P28D, MONTHS.between 0, DAYS.between 28
Jan 31 -> Mar 1:  Period P1M1D
P45D normalized: P45D, P13M normalized: P1Y1M
Jan 30 + P1M1D = 2025-03-01, Jan 30 + 1 day + 1 month = 2025-02-28
-- DST: night shift hours in Europe/Berlin
2025-03-29 22:00-06:00: LocalDateTime says 8 h, worked 7 h
2025-06-14 22:00-06:00: LocalDateTime says 8 h, worked 8 h
2025-10-25 22:00-06:00: LocalDateTime says 8 h, worked 9 h
Sat noon -> Sun noon: DAYS.between 1, HOURS.between 23, Duration.toDays 0
-- a daily 02:30 job across the spring gap
2025-03-30: derived 03:30, rebuilt 03:30
2025-03-31: derived 03:30, rebuilt 02:30
2025-04-01: derived 03:30, rebuilt 02:30
-- days without a midnight (America/Santiago)
atStartOfDay:        2024-09-08T01:00-03:00[America/Santiago]
atTime(0, 0).atZone: 2024-09-08T01:00-03:00[America/Santiago] (same, resolved by shifting)
hours in that day:   23
*/
