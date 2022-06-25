// Effective Java, Item 16: In public classes, use accessor methods, not public
// fields.
//
// A public field is a promise about representation: you cannot validate
// writes, keep invariants, run side effects on access, or switch to a
// different internal form later without breaking every client. Accessors
// (and mutators, if the class is mutable) keep those options open. Exposing
// fields is acceptable in a package-private or private nested class, where
// all users are under your control; and since Java 16 a record is the
// concise way to get a transparent carrier whose accessors you can still
// customize.
public class AccessorMethods {
    public static void main(String[] args) {
        // Public fields: nothing stops an invalid state
        ExposedTime exposed = new ExposedTime();
        exposed.hour = 27;
        exposed.minute = -5;
        System.out.println("public fields accept " + exposed.hour + ":" + exposed.minute);

        // Accessors: the class guards its invariants
        Time time = new Time(9, 30);
        try {
            time.setHour(27);
        } catch (IllegalArgumentException e) {
            System.out.println("mutator rejects it: " + e.getMessage());
        }
        time.setMinute(45);
        System.out.println("time: " + time.hour() + ":" + time.minute()
                + " (stored internally as " + time.minutesSinceMidnight() + " minutes)");

        // Records: public-field brevity, accessor-method flexibility
        Point p = new Point(3, 4);
        System.out.println("record accessors: x=" + p.x() + ", y=" + p.y() + ", " + p);
        try {
            new Point(-1, 0);
        } catch (IllegalArgumentException e) {
            System.out.println("record constructor rejects it: " + e.getMessage());
        }
    }
}

// Degenerate class: representation is the API
class ExposedTime {
    public int hour;
    public int minute;
}

// The representation changed (minutes since midnight) without changing the API
class Time {
    private int minutesSinceMidnight;

    Time(int hour, int minute) {
        setHour(hour);
        setMinute(minute);
    }

    public int hour() {
        return minutesSinceMidnight / 60;
    }

    public int minute() {
        return minutesSinceMidnight % 60;
    }

    public void setHour(int hour) {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("hour out of range: " + hour);
        }
        minutesSinceMidnight = hour * 60 + minute();
    }

    public void setMinute(int minute) {
        if (minute < 0 || minute > 59) {
            throw new IllegalArgumentException("minute out of range: " + minute);
        }
        minutesSinceMidnight = hour() * 60 + minute;
    }

    int minutesSinceMidnight() {
        return minutesSinceMidnight;
    }
}

record Point(int x, int y) {
    Point {
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("negative coordinate: (" + x + ", " + y + ")");
        }
    }
}

/* Output:
public fields accept 27:-5
mutator rejects it: hour out of range: 27
time: 9:45 (stored internally as 585 minutes)
record accessors: x=3, y=4, Point[x=3, y=4]
record constructor rejects it: negative coordinate: (-1, 0)
*/
