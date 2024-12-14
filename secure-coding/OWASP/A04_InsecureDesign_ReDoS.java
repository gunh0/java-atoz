// OWASP Top 10 2021: A04:2021 Insecure Design, regular expression denial of service (CWE-1333)
// https://owasp.org/Top10/A04_2021-Insecure_Design/
//
// java.util.regex is a backtracking engine: when a match fails, it tries
// every other way the pattern could have matched. Patterns with nested or
// overlapping quantifiers, such as (.*?,){11} or ((a+)+)+, have a huge
// number of such ways, so a short non-matching input can keep a thread busy
// for seconds or years. If attackers control the input (a header, a form
// field, a CSV upload), one request can pin a CPU. Since Java 9 the engine
// memoizes some simple cases like (a+)+, but not all. ReDoS has no Top 10
// entry of its own; it is filed here because the fix is a design decision:
// write patterns whose pieces cannot overlap (negated classes such as
// [^,]*, possessive ++ or atomic groups), bound input length before
// matching, and run untrusted matching under a work budget.
import java.util.regex.Pattern;

public class A04_InsecureDesign_ReDoS {
    // "Is the 12th CSV field a P-code?" A line with 20 numeric fields does not match.
    static final String LINE = "1,".repeat(20);
    static final Pattern VULNERABLE = Pattern.compile("^(.*?,){11}P");
    static final Pattern SAFE = Pattern.compile("^([^,\\r\\n]*+,){11}P");
    static final Pattern NESTED = Pattern.compile("((a+)+)+b");
    static final Pattern NESTED_FIXED = Pattern.compile("a+b");

    static final long READ_BUDGET = 1_000_000;    // character reads per match attempt
    static final long TIME_BUDGET_NANOS = 2_000_000_000L;

    public static void main(String[] args) {
        System.out.println("CSV line: 20 fields, no P");
        System.out.println("  noncompliant ^(.*?,){11}P        -> " + guardedFind(VULNERABLE, LINE));
        System.out.println("  compliant    ^([^,\\r\\n]*+,){11}P -> " + guardedFind(SAFE, LINE));

        String as = "a".repeat(24) + "!";
        System.out.println("24 a's and a '!'");
        System.out.println("  noncompliant ((a+)+)+b -> " + guardedFind(NESTED, as));
        System.out.println("  compliant    a+b       -> " + guardedFind(NESTED_FIXED, as));

        // Bounding input length is the cheapest guard of all
        String huge = "1,".repeat(10_000);
        System.out.println("10,000-field line, length limit 1,000 -> " + (huge.length() > 1_000 ? "rejected before matching" : guardedFind(SAFE, huge)));
    }

    // Runs find() on a CharSequence that aborts the match when the budget is spent
    static String guardedFind(Pattern p, String input) {
        BudgetedSequence seq = new BudgetedSequence(input, READ_BUDGET, System.nanoTime() + TIME_BUDGET_NANOS);
        try {
            return p.matcher(seq).find() ? "match" : "no match (within budget)";
        } catch (BudgetExceeded e) {
            return "aborted by guard (over " + READ_BUDGET + " reads or " + TIME_BUDGET_NANOS / 1_000_000_000 + " s)";
        }
    }

    static final class BudgetExceeded extends RuntimeException {
        BudgetExceeded() {
            super(null, null, false, false); // no stack trace: this is control flow
        }
    }

    static final class BudgetedSequence implements CharSequence {
        private final String s;
        private final long deadline;
        private long readsLeft;

        BudgetedSequence(String s, long reads, long deadline) {
            this.s = s;
            this.readsLeft = reads;
            this.deadline = deadline;
        }

        @Override
        public char charAt(int index) {
            if (--readsLeft < 0) throw new BudgetExceeded();
            if ((readsLeft & 0xFFFF) == 0 && System.nanoTime() > deadline) throw new BudgetExceeded();
            return s.charAt(index);
        }

        @Override
        public int length() {
            return s.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return s.subSequence(start, end);
        }

        @Override
        public String toString() {
            return s;
        }
    }
}

/* Output:
CSV line: 20 fields, no P
  noncompliant ^(.*?,){11}P        -> aborted by guard (over 1000000 reads or 2 s)
  compliant    ^([^,\r\n]*+,){11}P -> no match (within budget)
24 a's and a '!'
  noncompliant ((a+)+)+b -> aborted by guard (over 1000000 reads or 2 s)
  compliant    a+b       -> no match (within budget)
10,000-field line, length limit 1,000 -> rejected before matching
*/
