// OBJ06-J. Defensively copy mutable inputs and mutable internal components
// https://wiki.sei.cmu.edu/confluence/display/java/OBJ06-J.+Defensively+copy+mutable+inputs+and+mutable+internal+components
//
// A constructor or method that validates a mutable argument and then keeps
// the caller's reference has validated nothing: the caller still holds the
// same object and can change it after the check (a time-of-check /
// time-of-use gap). Copy mutable inputs FIRST, validate the copies, and
// store only the copies. Copy with a constructor or factory of a known
// class rather than clone(), which a malicious subclass of the argument
// type can override.
import java.util.Date;

public class OBJ06_DefensiveCopies {
    public static void main(String[] args) {
        Date start = new Date(1_000L);
        Date end = new Date(2_000L);

        LeaseNoncompliant bad = new LeaseNoncompliant(start, end);
        LeaseCompliant good = new LeaseCompliant(start, end);
        System.out.println("both accepted: start < end");

        // The caller keeps its references and moves the end before the start
        end.setTime(0L);
        System.out.println("noncompliant lease still valid? " + bad.isValid());
        System.out.println("compliant lease still valid?    " + good.isValid());

        // A subclass of Date can lie in clone(); a copy constructor cannot be overridden
        Date sneaky = new SneakyDate(5_000L);
        System.out.println("copy via clone():           " + ((Date) sneaky.clone()).getTime());
        System.out.println("copy via new Date(getTime): " + new Date(sneaky.getTime()).getTime());
    }
}

// Noncompliant: validates and stores the caller's objects
final class LeaseNoncompliant {
    private final Date start;
    private final Date end;

    LeaseNoncompliant(Date start, Date end) {
        if (!start.before(end)) {
            throw new IllegalArgumentException("start must be before end");
        }
        this.start = start;
        this.end = end;
    }

    boolean isValid() {
        return start.before(end);
    }
}

// Compliant: copies first, then validates and stores the copies
final class LeaseCompliant {
    private final Date start;
    private final Date end;

    LeaseCompliant(Date start, Date end) {
        this.start = new Date(start.getTime());
        this.end = new Date(end.getTime());
        if (!this.start.before(this.end)) {
            throw new IllegalArgumentException("start must be before end");
        }
    }

    boolean isValid() {
        return start.before(end);
    }

    // Internal components handed out are copies too
    Date start() {
        return new Date(start.getTime());
    }
}

// An attacker-supplied Date whose clone() returns something else
class SneakyDate extends Date {
    SneakyDate(long time) {
        super(time);
    }

    @Override
    public Object clone() {
        return new Date(0L);
    }
}

/* Output:
both accepted: start < end
noncompliant lease still valid? false
compliant lease still valid?    true
copy via clone():           0
copy via new Date(getTime): 5000
*/
