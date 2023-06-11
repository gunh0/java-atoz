// FIO08-J. Distinguish between characters or bytes read from a stream and -1
// https://wiki.sei.cmu.edu/confluence/display/java/FIO08-J.+Distinguish+between+characters+or+bytes+read+from+a+stream+and+-1
//
// InputStream.read() returns an int: a byte value 0..255, or -1 at end of
// stream. Reader.read() likewise returns a char value 0..65535 or -1.
// Casting to byte before the -1 check turns the legitimate byte 0xFF into
// -1, so reading stops early (and an attacker can truncate data at will by
// sending 0xFF). Casting to char makes -1 become U+FFFF, so the end of the
// stream is never detected and the loop runs forever. Keep the int, compare
// it with -1, and only then convert.
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;

public class FIO08_ReadReturnValue {
    public static void main(String[] args) throws IOException {
        byte[] data = {'a', 'b', (byte) 0xFF, 'c', 'd'};
        System.out.println("bytes read, noncompliant: " + countNoncompliant(new ByteArrayInputStream(data)));
        System.out.println("bytes read, compliant:    " + countCompliant(new ByteArrayInputStream(data)));

        System.out.println("chars read, noncompliant: " + readCharsNoncompliant(new StringReader("abc"), 5));
        System.out.println("chars read, compliant:    " + readCharsCompliant(new StringReader("abc")));
    }

    // Noncompliant: (byte) 0xFF == -1, so the data is cut off
    static int countNoncompliant(InputStream in) throws IOException {
        int count = 0;
        byte b;
        while ((b = (byte) in.read()) != -1) {
            count++;
        }
        return count;
    }

    // Compliant: test the int first
    static int countCompliant(InputStream in) throws IOException {
        int count = 0;
        int i;
        while ((i = in.read()) != -1) {
            byte b = (byte) i;
            count++;
        }
        return count;
    }

    // Noncompliant: (char) -1 is U+FFFF, never equal to -1; capped here so it ends
    static String readCharsNoncompliant(Reader in, int cap) throws IOException {
        StringBuilder sb = new StringBuilder();
        char c;
        int reads = 0;
        while ((c = (char) in.read()) != -1) {
            if (++reads > cap) {
                return sb + " ... still looping after " + cap + " reads";
            }
            sb.append(c == Character.MAX_VALUE ? "<FFFF>" : String.valueOf(c));
        }
        return sb.toString();
    }

    static String readCharsCompliant(Reader in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int i;
        while ((i = in.read()) != -1) {
            sb.append((char) i);
        }
        return sb.toString();
    }
}

/* Output:
bytes read, noncompliant: 2
bytes read, compliant:    5
chars read, noncompliant: abc<FFFF><FFFF> ... still looping after 5 reads
chars read, compliant:    abc
*/
