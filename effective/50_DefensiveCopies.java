// Effective Java, Item 50: Make defensive copies when needed.
//
// Program defensively: assume clients will try to break your invariants,
// by accident or on purpose. A class that stores or returns a mutable
// object (here java.util.Date) hands the caller a handle to its internals.
// Copy each mutable parameter on the way in, and validate the COPY, not the
// original (otherwise another thread can change it between the check and
// the copy). Do not use clone() on a parameter whose type can be subclassed
// by untrusted code. Copy again on the way out. Better still, use immutable
// types such as java.time.Instant, and the problem disappears.
import java.time.Instant;
import java.util.Date;

public class DefensiveCopies {
    public static void main(String[] args) {
        // Attack 1: mutate the argument after construction
        Date start = new Date(1_000);
        Date end = new Date(2_000);
        NaivePeriod naive = new NaivePeriod(start, end);
        end.setTime(0);
        System.out.println("naive, after mutating the argument: " + naive + " valid? " + naive.isValid());

        // Attack 2: mutate the value returned by an accessor
        NaivePeriod naive2 = new NaivePeriod(new Date(1_000), new Date(2_000));
        naive2.end().setTime(0);
        System.out.println("naive, after mutating the accessor: " + naive2 + " valid? " + naive2.isValid());

        // Defensive copies in and out
        Date s = new Date(1_000);
        Date e = new Date(2_000);
        Period safe = new Period(s, e);
        e.setTime(0);
        safe.end().setTime(0);
        System.out.println("defensive copies, both attacks:     " + safe + " valid? " + safe.isValid());

        // The check runs on the copies, so invalid input is still rejected
        try {
            new Period(new Date(2_000), new Date(1_000));
        } catch (IllegalArgumentException ex) {
            System.out.println("rejected: " + ex.getMessage());
        }

        // Immutable components: nothing to copy
        InstantPeriod modern = new InstantPeriod(Instant.ofEpochMilli(1_000), Instant.ofEpochMilli(2_000));
        System.out.println("Instant-based period: " + modern);
    }
}

// Broken: the "immutable" period shares its Dates with the caller
final class NaivePeriod {
    private final Date start;
    private final Date end;

    NaivePeriod(Date start, Date end) {
        if (start.compareTo(end) > 0) {
            throw new IllegalArgumentException(start.getTime() + " after " + end.getTime());
        }
        this.start = start;
        this.end = end;
    }

    Date start() {
        return start;
    }

    Date end() {
        return end;
    }

    boolean isValid() {
        return start.compareTo(end) <= 0;
    }

    @Override
    public String toString() {
        return start.getTime() + " - " + end.getTime();
    }
}

final class Period {
    private final Date start;
    private final Date end;

    Period(Date start, Date end) {
        // Copy first (new Date, not clone: Date can be subclassed), then check the copies
        this.start = new Date(start.getTime());
        this.end = new Date(end.getTime());
        if (this.start.compareTo(this.end) > 0) {
            throw new IllegalArgumentException(this.start.getTime() + " after " + this.end.getTime());
        }
    }

    Date start() {
        return new Date(start.getTime());
    }

    Date end() {
        return new Date(end.getTime());
    }

    boolean isValid() {
        return start.compareTo(end) <= 0;
    }

    @Override
    public String toString() {
        return start.getTime() + " - " + end.getTime();
    }
}

record InstantPeriod(Instant start, Instant end) {
    InstantPeriod {
        if (start.isAfter(end)) {
            throw new IllegalArgumentException(start + " after " + end);
        }
    }
}

/* Output:
naive, after mutating the argument: 1000 - 0 valid? false
naive, after mutating the accessor: 1000 - 0 valid? false
defensive copies, both attacks:     1000 - 2000 valid? true
rejected: 2000 after 1000
Instant-based period: InstantPeriod[start=1970-01-01T00:00:01Z, end=1970-01-01T00:00:02Z]
*/
