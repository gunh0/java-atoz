// isEmpty() is true only when length() == 0. isBlank() (Java 11) is also true
// for strings made of whitespace only. Neither can be called on null, so a
// "has text" check needs an explicit null test first.
//
//   value        isEmpty  isBlank
//   ""           true     true
//   "   "        false    true
//   "\t\n"       false    true
//   " a "        false    false
//
// A typical form-validation helper therefore looks like
// `s == null || s.isBlank()`.
public class IsEmptyExample {
    public static void main(String[] args) {
        String[] samples = {"", "   ", "\t\n", " a ", "\u2003"};
        String[] labels = {"\"\"", "\"   \"", "\"\\t\\n\"", "\" a \"", "EM SPACE"};
        for (int i = 0; i < samples.length; i++) {
            System.out.printf("%-10s isEmpty=%-5s isBlank=%s%n",
                    labels[i], samples[i].isEmpty(), samples[i].isBlank());
        }

        // Calling either on null throws
        String nothing = null;
        try {
            nothing.isEmpty();
        } catch (NullPointerException e) {
            System.out.println("null.isEmpty() -> NullPointerException");
        }

        // A null-safe check, used to validate a sign-up form
        System.out.println(validate("alice", "secret"));
        System.out.println(validate("   ", "secret"));
        System.out.println(validate("bob", ""));

        // isEmpty() is just a length check, so it is the same as length() == 0
        String s = "";
        System.out.println("isEmpty() == (length() == 0): " + (s.isEmpty() == (s.length() == 0)));
    }

    static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    static String validate(String username, String password) {
        if (!hasText(username)) return "rejected: username is required";
        if (password == null || password.isEmpty()) return "rejected: password is required";
        return "ok: " + username.strip();
    }
}

/* Output:
""         isEmpty=true  isBlank=true
"   "      isEmpty=false isBlank=true
"\t\n"     isEmpty=false isBlank=true
" a "      isEmpty=false isBlank=false
EM SPACE   isEmpty=false isBlank=true
null.isEmpty() -> NullPointerException
ok: alice
rejected: username is required
rejected: password is required
isEmpty() == (length() == 0): true
*/
