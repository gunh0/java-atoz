// Effective Java, Item 57: Minimize the scope of local variables.
//
// Declare a local variable where it is first used, and initialise it there
// if you can. The smaller its scope, the fewer lines a reader must keep in
// mind and the fewer places it can be misused. Prefer for loops to while
// loops when the loop variable is not needed afterwards: the variable then
// exists only inside the loop, so a copy-and-paste mistake that refers to
// the previous loop's iterator is a compile error rather than a silent bug.
// Keep methods small and focused so their locals stay local, too.
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MinimizeLocalScope {
    static int expensiveCalls;

    public static void main(String[] args) {
        List<String> c = List.of("a", "b", "c");
        List<String> c2 = List.of("x", "y", "z");

        // While loops: the iterator outlives its loop...
        List<String> seen = new ArrayList<>();
        Iterator<String> i = c.iterator();
        while (i.hasNext()) {
            seen.add(i.next());
        }
        Iterator<String> i2 = c2.iterator();
        while (i.hasNext()) { // BUG: copy-paste, should be i2. Compiles fine.
            seen.add(i2.next());
        }
        System.out.println("while loops saw: " + seen + " (c2 silently skipped)");

        // For loops: each iterator is scoped to its loop
        seen.clear();
        for (Iterator<String> it = c.iterator(); it.hasNext();) {
            seen.add(it.next());
        }
        for (Iterator<String> it2 = c2.iterator(); it2.hasNext();) {
            seen.add(it2.next());
            // using "it" here would not compile: cannot find symbol
        }
        System.out.println("for loops saw:   " + seen);

        // Evaluate an expensive bound once, keeping n scoped to the loop
        StringBuilder sb = new StringBuilder();
        for (int j = 0, n = expensiveComputation(); j < n; j++) {
            sb.append(j);
        }
        System.out.println("loop with cached bound: " + sb + ", expensiveComputation called "
                + expensiveCalls + " time(s)");
    }

    static int expensiveComputation() {
        expensiveCalls++;
        return 5;
    }
}

/* Output:
while loops saw: [a, b, c] (c2 silently skipped)
for loops saw:   [a, b, c, x, y, z]
loop with cached bound: 01234, expensiveComputation called 1 time(s)
*/
