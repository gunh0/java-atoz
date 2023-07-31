// A TemporalAdjuster is a reusable date rule: "last day of the month", "next
// Friday", "first Monday of the month". date.with(adjuster) applies it and
// returns a new date. TemporalAdjusters has the common ones; a lambda
// (Temporal -> Temporal) is a custom one.
// ChronoUnit is the unit enum used for arithmetic and differences:
// ChronoUnit.DAYS.between(a, b), date.plus(2, ChronoUnit.WEEKS), and
// truncatedTo(ChronoUnit.HOURS) on time-based types.
// Typical use: scheduling (patch Tuesday, monthly key rotation, quarter end).
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;

public class TemporalAdjustersExample {
    // Custom adjuster: the next business day (skips Saturday and Sunday)
    static final TemporalAdjuster NEXT_BUSINESS_DAY = t -> {
        LocalDate d = LocalDate.from(t).plusDays(1);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.plusDays(1);
        }
        return t.with(d);
    };

    // Custom adjuster: last day of the current quarter
    static final TemporalAdjuster END_OF_QUARTER = t -> {
        LocalDate d = LocalDate.from(t);
        Month last = d.getMonth().firstMonthOfQuarter().plus(2);
        return t.with(LocalDate.of(d.getYear(), last, 1).with(TemporalAdjusters.lastDayOfMonth()));
    };

    public static void main(String[] args) {
        LocalDate d = LocalDate.of(2023, 7, 31); // a Monday
        System.out.println("date: " + d + " (" + d.getDayOfWeek() + ")");
        System.out.println("firstDayOfMonth:  " + d.with(TemporalAdjusters.firstDayOfMonth()));
        System.out.println("lastDayOfMonth:   " + d.with(TemporalAdjusters.lastDayOfMonth()));
        System.out.println("firstDayOfNextMonth: " + d.with(TemporalAdjusters.firstDayOfNextMonth()));
        System.out.println("next FRIDAY:      " + d.with(TemporalAdjusters.next(DayOfWeek.FRIDAY)));
        System.out.println("nextOrSame MONDAY: " + d.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)));
        System.out.println("previous SUNDAY:  " + d.with(TemporalAdjusters.previous(DayOfWeek.SUNDAY)));
        System.out.println("lastDayOfYear:    " + d.with(TemporalAdjusters.lastDayOfYear()));

        // Patch Tuesday: the second Tuesday of every month
        System.out.print("patch Tuesdays H2 2023:");
        for (int month = 7; month <= 12; month++) {
            LocalDate patchDay = LocalDate.of(2023, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.TUESDAY));
            System.out.print(" " + patchDay);
        }
        System.out.println();

        // Custom adjusters
        LocalDate friday = LocalDate.of(2023, 8, 4);
        System.out.println("next business day after " + friday + " (" + friday.getDayOfWeek() + "): " + friday.with(NEXT_BUSINESS_DAY));
        System.out.println("end of quarter for " + d + ": " + d.with(END_OF_QUARTER));

        // ChronoUnit arithmetic and differences
        LocalDate rotated = LocalDate.of(2023, 1, 15);
        System.out.println("key rotated " + rotated + ", " + ChronoUnit.DAYS.between(rotated, d) + " days / "
                + ChronoUnit.WEEKS.between(rotated, d) + " weeks / " + ChronoUnit.MONTHS.between(rotated, d) + " months ago");
        System.out.println("rotation was due (+6 months): " + rotated.plus(6, ChronoUnit.MONTHS));
    }
}

/* Output:
date: 2023-07-31 (MONDAY)
firstDayOfMonth:  2023-07-01
lastDayOfMonth:   2023-07-31
firstDayOfNextMonth: 2023-08-01
next FRIDAY:      2023-08-04
nextOrSame MONDAY: 2023-07-31
previous SUNDAY:  2023-07-30
lastDayOfYear:    2023-12-31
patch Tuesdays H2 2023: 2023-07-11 2023-08-08 2023-09-12 2023-10-10 2023-11-14 2023-12-12
next business day after 2023-08-04 (FRIDAY): 2023-08-07
end of quarter for 2023-07-31: 2023-09-30
key rotated 2023-01-15, 197 days / 28 weeks / 6 months ago
rotation was due (+6 months): 2023-07-15
*/
