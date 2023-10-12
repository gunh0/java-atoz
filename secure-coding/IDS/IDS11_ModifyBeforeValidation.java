// IDS11-J. Perform any string modifications before validation
// https://wiki.sei.cmu.edu/confluence/display/java/IDS11-J.+Perform+any+string+modifications+before+validation
//
// Validation is only meaningful for the exact string that is used
// afterwards. If a filter first checks the input and then modifies it, for
// example by deleting noncharacter code points (U+FDD0..U+FDEF, U+FFFE,
// U+FFFF) or other "junk" characters, the deletion can join harmless
// fragments into a forbidden token: "<scr" + U+FDEF + "ipt>" passes the
// check and becomes "<script>". Do every modification first and validate
// the final result. Better still, replace removed characters with U+FFFD
// instead of deleting them, so that nothing can be glued together.
import java.text.Normalizer;
import java.util.regex.Pattern;

public class IDS11_ModifyBeforeValidation {
    private static final Pattern SCRIPT = Pattern.compile("<script", Pattern.CASE_INSENSITIVE);
    private static final Pattern NONCHARACTERS = Pattern.compile("[\\p{Cn}]");

    public static void main(String[] args) {
        String input = "<scr\uFDEFipt>alert(1)</scr\uFDEFipt>";
        System.out.println("noncompliant: " + filterNoncompliant(input));
        System.out.println("compliant:    " + filterCompliant(input));
        System.out.println("compliant, harmless input: " + filterCompliant("hello\uFDEF world"));
    }

    // Noncompliant: validates, THEN deletes unassigned code points
    static String filterNoncompliant(String s) {
        s = Normalizer.normalize(s, Normalizer.Form.NFKC);
        if (SCRIPT.matcher(s).find()) {
            return "rejected";
        }
        return "accepted, stored as " + NONCHARACTERS.matcher(s).replaceAll("");
    }

    // Compliant: normalizes and replaces first, then validates the final string
    static String filterCompliant(String s) {
        s = Normalizer.normalize(s, Normalizer.Form.NFKC);
        s = NONCHARACTERS.matcher(s).replaceAll("\uFFFD");
        if (SCRIPT.matcher(s).find()) {
            return "rejected";
        }
        return "accepted, stored as " + s.replace('\uFFFD', '?');
    }
}

/* Output:
noncompliant: accepted, stored as <script>alert(1)</script>
compliant:    accepted, stored as <scr?ipt>alert(1)</scr?ipt>
compliant, harmless input: accepted, stored as hello? world
*/
