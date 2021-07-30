// JEP 361 — Switch Expressions (Java 14)
//
// The classic switch statement falls through by default (a forgotten break
// is a silent bug), each case needs its own break, and producing a value
// means assigning a variable declared outside the switch. Since Java 14,
// switch can be an expression: `case A, B ->` labels never fall through,
// several constants share one label, `yield` returns a value from a block,
// and for enums the compiler checks that every constant is covered, so no
// default is needed.
public class SwitchExpressions {
    enum Day { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

    public static void main(String[] args) {
        for (Day day : Day.values()) {
            System.out.println(day + ": old=" + lettersOld(day) + ", new=" + letters(day) + ", " + kind(day));
        }

        // A classic fall-through bug: the missing break runs the next case too
        System.out.println("buggy old switch for 1: " + buggyOld(1));
        System.out.println("http 404 -> " + describe(404) + ", 503 -> " + describe(503) + ", 302 -> " + describe(302));
    }

    // Before: a statement, a mutable local, and a break on every case
    static int lettersOld(Day day) {
        int numLetters;
        switch (day) {
            case MONDAY:
            case FRIDAY:
            case SUNDAY:
                numLetters = 6;
                break;
            case TUESDAY:
                numLetters = 7;
                break;
            case THURSDAY:
            case SATURDAY:
                numLetters = 8;
                break;
            case WEDNESDAY:
                numLetters = 9;
                break;
            default:
                throw new IllegalStateException("unknown day: " + day);
        }
        return numLetters;
    }

    // After: an expression; exhaustive over the enum, so no default is needed
    static int letters(Day day) {
        return switch (day) {
            case MONDAY, FRIDAY, SUNDAY -> 6;
            case TUESDAY -> 7;
            case THURSDAY, SATURDAY -> 8;
            case WEDNESDAY -> 9;
        };
    }

    // A block on the right-hand side returns its value with yield
    static String kind(Day day) {
        return switch (day) {
            case SATURDAY, SUNDAY -> "weekend";
            default -> {
                int left = Day.FRIDAY.ordinal() - day.ordinal();
                yield left == 0 ? "last workday" : left + " workday(s) to go";
            }
        };
    }

    static String buggyOld(int n) {
        String result = "";
        switch (n) {
            case 1:
                result += "one ";   // forgot break: falls through into case 2
            case 2:
                result += "two ";
                break;
            default:
                result += "many";
        }
        return result.trim();
    }

    // Arrow labels on int values, with a default for the open set
    static String describe(int status) {
        return switch (status / 100) {
            case 2 -> "success";
            case 3 -> "redirect";
            case 4 -> status == 404 ? "not found" : "client error";
            case 5 -> "server error";
            default -> "unknown";
        };
    }
}

/* Output:
MONDAY: old=6, new=6, 4 workday(s) to go
TUESDAY: old=7, new=7, 3 workday(s) to go
WEDNESDAY: old=9, new=9, 2 workday(s) to go
THURSDAY: old=8, new=8, 1 workday(s) to go
FRIDAY: old=6, new=6, last workday
SATURDAY: old=8, new=8, weekend
SUNDAY: old=6, new=6, weekend
buggy old switch for 1: one two
http 404 -> not found, 503 -> server error, 302 -> redirect
*/
