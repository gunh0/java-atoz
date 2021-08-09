// Effective Java, Item 12: Always override toString.
//
// Object.toString returns ClassName@hexHashCode, which tells a reader nothing
// in a log line, a debugger, an assertion message or a collection printout.
// Override it to return a concise, informative representation of all the
// interesting state. Decide whether the format is part of the API: if you
// document an exact format (e.g. "(707) 867-5309"), provide a matching
// parser/static factory and never change it; if not, say it may change.
// Either way, expose every value in toString through accessors too, so no one
// has to parse the string. Never leak secrets (passwords, tokens) in toString.
import java.util.List;
import java.util.Map;

public class ToStringDemo {
    public static void main(String[] args) {
        // Default toString: class name + hash code (varies between runs, so
        // only its shape is shown here)
        String defaultString = new PlainPhone(707, 867, 5309).toString();
        System.out.println("default:  " + defaultString.replaceAll("@\\p{XDigit}+$", "@<hash>"));

        // Documented, fixed format with a matching parser
        PhoneNumber jenny = new PhoneNumber(707, 867, 5309);
        System.out.println("override: " + jenny);
        System.out.println("parsed back equals: " + PhoneNumber.parse(jenny.toString()).toString().equals(jenny.toString()));

        // toString is used automatically by string concatenation and collections
        Map<String, PhoneNumber> book = Map.of("Jenny", jenny);
        System.out.println("in a map: " + book);
        System.out.println("in a list: " + List.of(jenny, new PhoneNumber(212, 555, 12)));

        // Sensitive fields are masked
        System.out.println(new Credentials("admin", "hunter2"));
    }
}

class PlainPhone {
    private final int areaCode, prefix, lineNum;

    PlainPhone(int areaCode, int prefix, int lineNum) {
        this.areaCode = areaCode;
        this.prefix = prefix;
        this.lineNum = lineNum;
    }
}

final class PhoneNumber {
    private final short areaCode, prefix, lineNum;

    PhoneNumber(int areaCode, int prefix, int lineNum) {
        this.areaCode = (short) areaCode;
        this.prefix = (short) prefix;
        this.lineNum = (short) lineNum;
    }

    // Accessors: the data in toString is also available programmatically
    int areaCode() { return areaCode; }
    int prefix() { return prefix; }
    int lineNum() { return lineNum; }

    /**
     * Returns the string representation of this phone number in the form
     * "(XXX) YYY-ZZZZ": area code, prefix and line number, zero-padded.
     * This format is fixed and is accepted by {@link #parse(String)}.
     */
    @Override
    public String toString() {
        return String.format("(%03d) %03d-%04d", areaCode, prefix, lineNum);
    }

    static PhoneNumber parse(String s) {
        if (!s.matches("\\(\\d{3}\\) \\d{3}-\\d{4}")) {
            throw new IllegalArgumentException("not a phone number: " + s);
        }
        return new PhoneNumber(Integer.parseInt(s.substring(1, 4)),
                Integer.parseInt(s.substring(6, 9)),
                Integer.parseInt(s.substring(10)));
    }
}

final class Credentials {
    private final String user;
    private final String password;

    Credentials(String user, String password) {
        this.user = user;
        this.password = password;
    }

    // Unspecified format; the secret is never printed
    @Override
    public String toString() {
        return "Credentials{user=" + user + ", password=****}";
    }
}

/* Output:
default:  PlainPhone@<hash>
override: (707) 867-5309
parsed back equals: true
in a map: {Jenny=(707) 867-5309}
in a list: [(707) 867-5309, (212) 555-0012]
Credentials{user=admin, password=****}
*/
