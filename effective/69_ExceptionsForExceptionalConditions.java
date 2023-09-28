// Effective Java, Item 69: Use exceptions only for exceptional conditions.
//
// Exceptions are for exceptional situations, never for ordinary control
// flow. Ending a loop by catching ArrayIndexOutOfBoundsException is slower
// than the plain loop, obscures intent, and - worst of all - swallows any
// *real* bug that throws the same exception inside the loop body, turning
// a crash into a silently wrong answer. Likewise an API should not force
// clients to use exceptions for control flow: offer a state-testing method
// (hasNext/next) or a distinguished return value (Optional, -1, null).
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class ExceptionsForExceptionalConditions {
    public static void main(String[] args) {
        int[] values = {3, 1, 4, 1, 5};
        int[] weights = {1, 1, 1, 1}; // bug: one weight short

        // Anti-pattern: exception as the loop terminator
        int sum = 0;
        try {
            int i = 0;
            while (true) {
                sum += values[i] * weights[i]; // the real bug throws here...
                i++;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            // ...and is mistaken for "end of array"
        }
        System.out.println("exception-terminated loop: sum = " + sum + " (bug hidden)");

        // Recommended: the standard idiom; the bug surfaces immediately
        try {
            int total = 0;
            for (int i = 0; i < values.length; i++) {
                total += values[i] * weights[i];
            }
            System.out.println("for loop: sum = " + total);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("for loop: bug found -> " + e.getMessage());
        }

        // API design: state-testing method vs distinguished return value
        List<String> users = List.of("ann", "bob", "carol");
        for (Iterator<String> it = users.iterator(); it.hasNext(); ) {
            System.out.print(it.next() + " ");
        }
        System.out.println("<- iterated with hasNext()");
        System.out.println("find(\"bob\")  = " + find(users, "bob"));
        System.out.println("find(\"dave\") = " + find(users, "dave"));
    }

    static Optional<Integer> find(List<String> list, String name) {
        int i = list.indexOf(name);
        return i < 0 ? Optional.empty() : Optional.of(i);
    }
}

/* Output:
exception-terminated loop: sum = 9 (bug hidden)
for loop: bug found -> Index 4 out of bounds for length 4
ann bob carol <- iterated with hasNext()
find("bob")  = Optional[1]
find("dave") = Optional.empty
*/
