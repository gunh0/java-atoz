// substring(begin) returns the text from index `begin` to the end;
// substring(begin, end) returns the characters from `begin` (inclusive) to
// `end` (exclusive), so its length is end - begin. Indexes outside
// 0 <= begin <= end <= length() throw StringIndexOutOfBoundsException.
// Since Java 7u6 the result is an independent copy (it no longer shares the
// original char array), so taking a small substring of a huge string does not
// keep the huge one alive.
public class SubstringExample {
    public static void main(String[] args) {
        String s = "Hello, World";

        System.out.println(s.substring(7));       // World
        System.out.println(s.substring(0, 5));    // Hello
        System.out.println(s.substring(7, 8));    // W
        System.out.println("[" + s.substring(5, 5) + "]"); // empty: begin == end
        System.out.println(s.substring(0) == s);  // true: the whole string is returned as is

        // Length of the result is always end - begin
        int begin = 3, end = 9;
        System.out.println(s.substring(begin, end) + " has length " + s.substring(begin, end).length());

        // Combine with indexOf to extract parts
        String email = "user.name@example.com";
        int at = email.indexOf('@');
        System.out.println("local part: " + email.substring(0, at));
        System.out.println("domain: " + email.substring(at + 1));

        // Last n characters
        String card = "4111111111111234";
        System.out.println("**** " + card.substring(card.length() - 4));

        // Invalid ranges
        try {
            s.substring(5, 3);
        } catch (StringIndexOutOfBoundsException e) {
            System.out.println("substring(5, 3): " + e.getMessage());
        }
        try {
            s.substring(0, 50);
        } catch (StringIndexOutOfBoundsException e) {
            System.out.println("substring(0, 50): " + e.getMessage());
        }
    }
}

/* Output:
World
Hello
W
[]
true
lo, Wo has length 6
local part: user.name
domain: example.com
**** 1234
substring(5, 3): begin 5, end 3, length 12
substring(0, 50): begin 0, end 50, length 12
*/
