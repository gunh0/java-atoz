// Java 21 — String.indexOf(ch/str, beginIndex, endIndex) and
// StringBuilder.repeat (API additions, no JEP)
//
// Searching only part of a string used to mean either substring() (which
// copies the characters and shifts every returned index, an off-by-offset
// bug waiting to happen) or indexOf(ch, from) followed by a check that the
// hit is not past the end of the region, after it may have scanned the
// whole rest of the string. Java 21 adds indexOf overloads with an end
// index: the search is bounded, indexes stay relative to the whole
// string, and an invalid range throws instead of being silently clamped.
// StringBuilder (and StringBuffer) gained repeat(CharSequence, n) and
// repeat(codePoint, n), so padding and rules no longer need a loop or a
// temporary String.repeat() result.
public class StringIndexOfRangeAndRepeat {
    public static void main(String[] args) {
        // A header line with fields separated by ';', we search only inside the second field
        String header = "GET /a;Host: example.org:8443;Accept: */*";
        int start = header.indexOf(';') + 1;
        int end = header.indexOf(';', start);
        System.out.println("field 2 = [" + start + "," + end + "): " + header.substring(start, end));

        // Before (a): substring, then shift the index back
        int colonInCopy = header.substring(start, end).indexOf(':');
        System.out.println("before: via substring -> " + (start + colonInCopy) + " (copy + manual offset)");

        // Before (b): search from start, then check it is still inside the region
        int hit = header.indexOf('*', start);
        System.out.println("before: indexOf('*', start) -> " + hit + ", inside field 2: " + (hit != -1 && hit < end));

        // After: bounded search, absolute index, -1 when not in the region
        System.out.println("after:  indexOf('*', start, end) -> " + header.indexOf('*', start, end));
        System.out.println("after:  indexOf(\"example\", start, end) -> " + header.indexOf("example", start, end));
        System.out.println("after:  indexOf(':', start, end) -> " + header.indexOf(':', start, end));

        // Unlike the lenient indexOf(ch, from), an invalid range is a programming error
        try {
            header.indexOf('G', 5, 1000);
        } catch (StringIndexOutOfBoundsException e) {
            System.out.println("bad range: " + e.getMessage());
        }

        // StringBuilder.repeat: build a text table without loops
        String[][] rows = {{"user", "failures"}, {"mallory", "7"}, {"bob", "1"}};
        StringBuilder table = new StringBuilder();
        for (String[] row : rows) {
            table.append(row[0]).repeat(' ', 10 - row[0].length()).append("| ").append(row[1]).append('\n');
            if (row == rows[0]) {
                table.repeat('-', 10).append('+').repeat("-", 10).append('\n');
            }
        }
        System.out.print(table);

        // Code points outside the BMP work too (one code point = two chars)
        StringBuilder stars = new StringBuilder().repeat(0x2B50, 3).append(' ').repeat(0x1F512, 2);
        System.out.println("3 x U+2B50, a space, 2 x U+1F512: " + stars.codePointCount(0, stars.length())
                + " code points, " + stars.length() + " chars");

        // Before: a loop or an intermediate String
        StringBuilder old = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            old.append("ab");
        }
        System.out.println("loop: " + old + ", repeat: " + new StringBuilder().repeat("ab", 3));
    }
}

/* Output:
field 2 = [7,29): Host: example.org:8443
before: via substring -> 11 (copy + manual offset)
before: indexOf('*', start) -> 38, inside field 2: false
after:  indexOf('*', start, end) -> -1
after:  indexOf("example", start, end) -> 13
after:  indexOf(':', start, end) -> 11
bad range: Range [5, 1000) out of bounds for length 41
user      | failures
----------+----------
mallory   | 7
bob       | 1
3 x U+2B50, a space, 2 x U+1F512: 6 code points, 8 chars
loop: ababab, repeat: ababab
*/
