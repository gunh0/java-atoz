// MET06-J. Do not invoke overridable methods in clone()
// https://wiki.sei.cmu.edu/confluence/display/java/MET06-J.+Do+not+invoke+overridable+methods+in+clone()
//
// Object.clone() makes a shallow copy, and clone() implementations then fix
// up the copy (deep-copy mutable fields, reset caches). If clone() calls an
// overridable method on the copy BEFORE that fix-up is done, a subclass
// override runs against an object that still shares its mutable state with
// the original, and whatever it does to "its" data happens to the original
// too. Call only private or final methods from clone(), and finish the deep
// copy before calling anything else. (The same reasoning applies to
// constructors, MET05-J.)
import java.util.ArrayList;
import java.util.List;

public class MET06_CloneOverridableMethods {
    public static void main(String[] args) throws CloneNotSupportedException {
        SessionNoncompliant original = new RedactingSessionNoncompliant();
        original.cookies.add("session=abc123");
        original.clone();
        System.out.println("noncompliant, original's cookies after clone(): " + original.cookies);

        SessionCompliant ok = new RedactingSessionCompliant();
        ok.cookies.add("session=abc123");
        SessionCompliant copy = ok.clone();
        System.out.println("compliant,    original's cookies after clone(): " + ok.cookies + ", copy: " + copy.cookies);
    }
}

class SessionNoncompliant implements Cloneable {
    List<String> cookies = new ArrayList<>();

    @Override
    public SessionNoncompliant clone() throws CloneNotSupportedException {
        SessionNoncompliant copy = (SessionNoncompliant) super.clone();
        copy.prepareCopy(); // Noncompliant: overridable, and copy.cookies is still shared
        copy.cookies = new ArrayList<>(copy.cookies);
        return copy;
    }

    void prepareCopy() {}
}

// A subclass that redacts the copy, as it believes
class RedactingSessionNoncompliant extends SessionNoncompliant {
    @Override
    void prepareCopy() {
        cookies.replaceAll(c -> c.replaceAll("=.*", "=<redacted>"));
    }
}

class SessionCompliant implements Cloneable {
    List<String> cookies = new ArrayList<>();

    // Compliant: calls no overridable methods; the copy is independent when returned
    @Override
    public SessionCompliant clone() {
        try {
            SessionCompliant copy = (SessionCompliant) super.clone();
            copy.cookies = copyCookies();
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    private List<String> copyCookies() {
        return new ArrayList<>(cookies);
    }
}

// The subclass adjusts the finished, independent copy in its own clone()
class RedactingSessionCompliant extends SessionCompliant {
    @Override
    public RedactingSessionCompliant clone() {
        RedactingSessionCompliant copy = (RedactingSessionCompliant) super.clone();
        copy.cookies.replaceAll(c -> c.replaceAll("=.*", "=<redacted>"));
        return copy;
    }
}

/* Output:
noncompliant, original's cookies after clone(): [session=<redacted>]
compliant,    original's cookies after clone(): [session=abc123], copy: [session=<redacted>]
*/
