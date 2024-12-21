// Misuse: keeping passwords in String instead of char[]
//
// Strings are immutable and may be shared or interned, so a password held
// in a String stays in the heap until the garbage collector happens to
// reuse that memory; it can show up in heap dumps, core files and swap, and
// nothing in your code can erase it. That is why the JDK's own APIs hand out
// passwords as char[]: Console.readPassword, JPasswordField.getPassword,
// KeyStore.load, PBEKeySpec. Keep it as char[] from input to use, wipe it
// with Arrays.fill in a finally block, and do not put it into objects whose
// toString() ends up in logs (records print all their components). This is
// defence in depth, not a guarantee: the GC may have copied the array
// before you wiped it, but you shorten the exposure window considerably.
import java.lang.reflect.Field;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class PasswordsAsStrings {
    record LoginRequest(String user, String password) { }                 // noncompliant
    record SafeLoginRequest(String user, char[] password) {               // compliant
        @Override public String toString() { return "SafeLoginRequest[user=" + user + ", password=***]"; }
    }

    public static void main(String[] args) throws Exception {
        // Noncompliant 1: the String cannot be wiped; "clearing" makes a new String
        String pw = new String(new char[] {'h', 'u', 'n', 't', 'e', 'r', '2'});
        String cleared = pw.replaceAll(".", "\0");
        System.out.println("String after \"wiping\": still \"" + pw + "\" (replaceAll returned a copy: " + (cleared != pw) + ")");

        // ...and its internal array is off limits, even to reflection
        try {
            Field value = String.class.getDeclaredField("value");
            value.setAccessible(true);
        } catch (RuntimeException e) {
            System.out.println("reflective access to String.value: " + e.getClass().getSimpleName());
        }

        // Noncompliant 2: a record's generated toString puts it in the log
        System.out.println("log line: " + new LoginRequest("alice", pw));

        // Compliant: char[] end to end, wiped after use
        char[] secret = {'h', 'u', 'n', 't', 'e', 'r', '2'};
        SafeLoginRequest req = new SafeLoginRequest("alice", secret);
        System.out.println("log line: " + req);
        try {
            PBEKeySpec spec = new PBEKeySpec(req.password(), new byte[16], 10_000, 256);
            try {
                byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
                System.out.println("derived a " + key.length * 8 + "-bit key from the char[]");
            } finally {
                spec.clearPassword(); // PBEKeySpec holds its own copy: wipe it too
            }
            try {
                spec.getPassword();
            } catch (IllegalStateException e) {
                System.out.println("PBEKeySpec after clearPassword(): " + e.getClass().getSimpleName());
            }
        } finally {
            Arrays.fill(secret, '\0');
        }
        boolean allZero = true;
        for (char c : secret) allZero &= c == '\0';
        System.out.println("char[] after Arrays.fill: all zero = " + allZero);
    }
}

/* Output:
String after "wiping": still "hunter2" (replaceAll returned a copy: true)
reflective access to String.value: InaccessibleObjectException
log line: LoginRequest[user=alice, password=hunter2]
log line: SafeLoginRequest[user=alice, password=***]
derived a 256-bit key from the char[]
PBEKeySpec after clearPassword(): IllegalStateException
char[] after Arrays.fill: all zero = true
*/
