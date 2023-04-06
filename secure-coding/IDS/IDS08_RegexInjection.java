// IDS08-J. Sanitize untrusted data included in a regular expression
// https://wiki.sei.cmu.edu/confluence/display/java/IDS08-J.+Sanitize+untrusted+data+included+in+a+regular+expression
//
// When user input is pasted into a regular expression, its metacharacters
// change the meaning of the pattern. A search that is meant to match one
// keyword inside the caller's own records can be widened with ".*" or an
// alternation "|" to match other users' records, or given a pattern with
// catastrophic backtracking that ties up the CPU. Treat input as a literal:
// quote it with Pattern.quote() (or escape metacharacters, or allow only a
// safe character set) before building the pattern.
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class IDS08_RegexInjection {
    // A log searched with "<user>:<message>" lines; callers may only see their own lines
    private static final String LOG = String.join("\n",
            "alice:login ok",
            "bob:password reset token=8f2a91",
            "alice:search shoes",
            "carol:card ending 4242 charged");

    public static void main(String[] args) {
        String[] searches = {"search", "x|.*token", "x|carol"};
        for (String keyword : searches) {
            System.out.println("alice searches \"" + keyword + "\"");
            System.out.println("  noncompliant: " + searchNoncompliant("alice", keyword));
            System.out.println("  compliant:    " + searchCompliant("alice", keyword));
        }
    }

    // Noncompliant: the keyword is inserted into the pattern as-is
    static List<String> searchNoncompliant(String user, String keyword) {
        return find("(?m)^(" + user + ":.*" + keyword + ".*)$");
    }

    // Compliant: the keyword is quoted, so it only ever matches literally
    static List<String> searchCompliant(String user, String keyword) {
        return find("(?m)^(" + Pattern.quote(user) + ":.*" + Pattern.quote(keyword) + ".*)$");
    }

    static List<String> find(String regex) {
        List<String> hits = new ArrayList<>();
        var m = Pattern.compile(regex).matcher(LOG);
        while (m.find()) {
            hits.add(m.group());
        }
        return hits;
    }
}

/* Output:
alice searches "search"
  noncompliant: [alice:search shoes]
  compliant:    [alice:search shoes]
alice searches "x|.*token"
  noncompliant: [bob:password reset token=8f2a91]
  compliant:    []
alice searches "x|carol"
  noncompliant: [carol:card ending 4242 charged]
  compliant:    []
*/
