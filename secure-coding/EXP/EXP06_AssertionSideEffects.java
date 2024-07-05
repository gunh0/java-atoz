// EXP06-J. Expressions used in assertions must not produce side effects
// https://wiki.sei.cmu.edu/confluence/display/java/EXP06-J.+Expressions+used+in+assertions+must+not+produce+side+effects
//
// An assert statement's expression is evaluated only when assertions are
// enabled (-ea). Put real work inside it (removing an element, consuming a
// token, incrementing a counter) and the program behaves differently in
// testing, where -ea is common, and in production, where it is off. The
// work silently disappears exactly where it matters. Do the work in a
// normal statement, keep the result in a variable, and assert only on that
// variable.
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EXP06_AssertionSideEffects {
    public static void main(String[] args) {
        System.out.println("assertions enabled: " + EXP06_AssertionSideEffects.class.desiredAssertionStatus());

        // 1. Clean a list of names
        List<String> names = new ArrayList<>(Arrays.asList("ann", null, "bob"));
        assert names.remove(null); // noncompliant: never runs without -ea
        System.out.println("noncompliant: names = " + names);

        List<String> names2 = new ArrayList<>(Arrays.asList("ann", null, "bob"));
        boolean removed = names2.remove(null); // compliant: the work always happens
        assert removed;
        System.out.println("compliant:    names = " + names2);

        // 2. One-time password tokens must be consumed on use
        Set<String> unused = new HashSet<>(Set.of("731942"));
        assert unused.remove("731942") : "token reuse";
        System.out.println("noncompliant: token 731942 still usable: " + unused.contains("731942"));

        Set<String> unused2 = new HashSet<>(Set.of("731942"));
        boolean consumed = unused2.remove("731942");
        if (!consumed) {
            throw new SecurityException("token reuse");
        }
        System.out.println("compliant:    token 731942 still usable: " + unused2.contains("731942"));
    }
}

/* Output:
assertions enabled: false
noncompliant: names = [ann, null, bob]
compliant:    names = [ann, bob]
noncompliant: token 731942 still usable: true
compliant:    token 731942 still usable: false
*/
