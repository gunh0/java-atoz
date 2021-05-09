// String.contains(CharSequence s) returns true if the sequence s occurs
// anywhere in the string. It is case-sensitive, it treats its argument as
// plain text (no regex), every string contains the empty string, and a null
// argument throws NullPointerException. Because the parameter type is
// CharSequence, a StringBuilder can be passed directly.
import java.util.Locale;

public class ContainsExample {
    public static void main(String[] args) {
        String sentence = "Java is a general-purpose programming language";

        System.out.println(sentence.contains("purpose"));   // true
        System.out.println(sentence.contains("Purpose"));   // false: case matters
        System.out.println(sentence.contains("a.*e"));      // false: not a regex
        System.out.println(sentence.contains(""));          // true: empty string is everywhere

        // Any CharSequence works
        StringBuilder needle = new StringBuilder("program");
        System.out.println(sentence.contains(needle));      // true

        // Case-insensitive check: normalize both sides with a fixed locale
        // (Locale.ROOT avoids surprises such as the Turkish dotless i)
        String query = "JAVA";
        boolean found = sentence.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
        System.out.println("contains \"" + query + "\" ignoring case: " + found);

        // contains() is just indexOf(s) >= 0
        System.out.println(sentence.contains("is") == (sentence.indexOf("is") >= 0));

        try {
            sentence.contains(null);
        } catch (NullPointerException e) {
            System.out.println("contains(null) throws NullPointerException");
        }
    }
}

/* Output:
true
false
false
true
true
contains "JAVA" ignoring case: true
true
contains(null) throws NullPointerException
*/
