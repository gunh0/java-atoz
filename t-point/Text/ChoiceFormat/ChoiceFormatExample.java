// ChoiceFormat maps numeric ranges to strings. Its pattern is a list of
// "limit#text" pairs separated by '|': a value picks the text of the largest
// limit that is <= the value ("limit<text" means strictly greater than the
// limit). Values below the first limit fall back to the first text.
// On its own it turns scores into grades; inside MessageFormat as
// {0,choice,...} it handles simple plurals, and the chosen text may contain
// nested placeholders such as {0,number,integer}.
// For real localisation with complex plural rules, keep one message per
// plural form per language; ChoiceFormat only knows numeric thresholds.
import java.text.ChoiceFormat;
import java.text.MessageFormat;
import java.util.Locale;

public class ChoiceFormatExample {
    public static void main(String[] args) {
        // Arrays form: limits must be ascending
        double[] limits = {0, 60, 70, 80, 90};
        String[] grades = {"F", "D", "C", "B", "A"};
        ChoiceFormat grade = new ChoiceFormat(limits, grades);
        for (int score : new int[] {-5, 0, 59, 60, 79, 80, 95, 100}) {
            System.out.println("score " + score + " -> " + grade.format(score));
        }

        // Pattern form; '<' means "greater than", '#' means "greater than or equal"
        ChoiceFormat files = new ChoiceFormat("0#no files|1#one file|1<many files");
        for (double n : new double[] {0, 1, 1.5, 2}) {
            System.out.println(n + " -> " + files.format(n));
        }
        System.out.println("toPattern: " + files.toPattern());

        // Plurals inside MessageFormat
        MessageFormat mf = new MessageFormat(
                "There {0,choice,0#are no failed logins|1#is one failed login|1<are {0,number,integer} failed logins} for {1}.",
                Locale.US);
        for (int n : new int[] {0, 1, 2, 1500}) {
            System.out.println(mf.format(new Object[] {n, "alice"}));
        }

        // nextDouble / previousDouble build the strict limits that '<' stands for
        System.out.println("nextDouble(1.0) > 1.0: " + (ChoiceFormat.nextDouble(1.0) > 1.0));
        double[] severityLimits = {Double.NEGATIVE_INFINITY, 4.0, 7.0, 9.0};
        String[] severities = {"low", "medium", "high", "critical"};
        ChoiceFormat cvss = new ChoiceFormat(severityLimits, severities);
        for (double score : new double[] {0.0, 3.9, 4.0, 6.9, 7.5, 9.8}) {
            System.out.println("CVSS " + score + " -> " + cvss.format(score));
        }
    }
}

/* Output:
score -5 -> F
score 0 -> F
score 59 -> F
score 60 -> D
score 79 -> C
score 80 -> B
score 95 -> A
score 100 -> A
0.0 -> no files
1.0 -> one file
1.5 -> many files
2.0 -> many files
toPattern: 0.0#no files|1.0#one file|1.0<many files
There are no failed logins for alice.
There is one failed login for alice.
There are 2 failed logins for alice.
There are 1,500 failed logins for alice.
nextDouble(1.0) > 1.0: true
CVSS 0.0 -> low
CVSS 3.9 -> low
CVSS 4.0 -> medium
CVSS 6.9 -> medium
CVSS 7.5 -> high
CVSS 9.8 -> critical
*/
