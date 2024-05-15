// Numbered groups break as soon as someone adds a group to the pattern.
// Named groups make regexes self-documenting and robust:
//   (?<name>...)   define a named capturing group (letters and digits only)
//   m.group("name") / m.start("name") / m.end("name")
//   \k<name>       backreference to what the group matched
//   ${name}        refer to the group in a replacement string
//   (?:...)        group without capturing, when you only need alternation
//                  or a quantifier
// Named groups still have numbers (counted by their opening parenthesis), and
// Matcher.namedGroups() (Java 20) maps names to those numbers.
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NamedGroupsExample {
    public static void main(String[] args) {
        Pattern logLine = Pattern.compile(
                "(?<date>(?<year>\\d{4})-(?<month>\\d{2})-(?<day>\\d{2})) (?<level>INFO|WARN|ERROR) (?<msg>.*)");
        Matcher m = logLine.matcher("2024-05-15 WARN disk usage at 91%");
        if (m.matches()) {
            System.out.println("date:  " + m.group("date"));
            System.out.println("year:  " + m.group("year") + ", month: " + m.group("month") + ", day: " + m.group("day"));
            System.out.println("level: " + m.group("level") + " (chars " + m.start("level") + ".." + m.end("level") + ")");
            System.out.println("msg:   " + m.group("msg"));
            System.out.println("group numbers: " + new TreeMap<>(m.namedGroups()));
        }

        // ${name} in the replacement: reorder ISO dates into day/month/year
        Pattern iso = Pattern.compile("(?<y>\\d{4})-(?<m>\\d{2})-(?<d>\\d{2})");
        System.out.println(iso.matcher("from 2024-05-01 to 2024-05-15").replaceAll("${d}/${m}/${y}"));

        // \k<name>: find accidentally doubled words
        Pattern doubled = Pattern.compile("\\b(?<word>\\w+)\\s+\\k<word>\\b", Pattern.CASE_INSENSITIVE);
        Matcher dm = doubled.matcher("This is is a test of the the scanner");
        while (dm.find()) {
            System.out.println("doubled word: \"" + dm.group("word") + "\" at " + dm.start());
        }

        // Non-capturing group: alternation without creating group 1
        Pattern host = Pattern.compile("(?:https?|ftp)://(?<host>[^/:]+)(?::(?<port>\\d+))?");
        for (String url : new String[] {"https://example.com/a", "ftp://files.local:2121/x", "http://10.0.0.1:8080"}) {
            Matcher hm = host.matcher(url);
            if (hm.lookingAt()) {
                String port = hm.group("port"); // null when the optional group did not take part
                System.out.println(url + " -> host=" + hm.group("host") + ", port=" + (port == null ? "(default)" : port)
                        + ", groupCount=" + hm.groupCount());
            }
        }

        // Asking for a name the pattern does not define is a programming error
        try {
            m.group("severity");
        } catch (IllegalArgumentException e) {
            System.out.println("unknown group name -> IllegalArgumentException");
        }

        Map<String, Integer> names = Pattern.compile("(?<a>x)(y)(?<b>z)").matcher("xyz").namedGroups();
        System.out.println("(?<a>x)(y)(?<b>z) -> " + new TreeMap<>(names));
    }
}

/* Output:
date:  2024-05-15
year:  2024, month: 05, day: 15
level: WARN (chars 11..15)
msg:   disk usage at 91%
group numbers: {date=1, day=4, level=5, month=3, msg=6, year=2}
from 01/05/2024 to 15/05/2024
doubled word: "is" at 5
doubled word: "the" at 21
https://example.com/a -> host=example.com, port=(default), groupCount=2
ftp://files.local:2121/x -> host=files.local, port=2121, groupCount=2
http://10.0.0.1:8080 -> host=10.0.0.1, port=8080, groupCount=2
unknown group name -> IllegalArgumentException
(?<a>x)(y)(?<b>z) -> {a=1, b=3}
*/
