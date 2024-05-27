// STR04-J. Use compatible character encodings when communicating string data between JVMs
// https://wiki.sei.cmu.edu/confluence/display/java/STR04-J.+Use+compatible+character+encodings+when+communicating+string+data+between+JVMs
//
// Strings cross a socket, a pipe or a file as bytes. If the writer and
// the reader assume different charsets, the text is silently altered:
// characters the writer's charset cannot represent become '?', and bytes
// the reader decodes differently become mojibake. Beyond garbled names,
// this breaks security decisions, because the value one side validated is
// not the value the other side uses. Agree on one charset (UTF-8) in the
// protocol and name it explicitly on both ends, or use a self-describing
// encoding such as DataOutputStream.writeUTF/readUTF.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class STR04_CompatibleEncodings {
    public static void main(String[] args) throws IOException {
        String[] names = {"José", "Ελένη", "김민수"};

        for (String name : names) {
            // Noncompliant 1: sender uses ISO-8859-1, receiver uses UTF-8
            String a = receive(send(name, StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
            // Noncompliant 2: sender uses UTF-8, receiver uses ISO-8859-1
            String b = receive(send(name, StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
            // Compliant: both sides say UTF-8
            String c = receive(send(name, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            System.out.println(name + ": latin1->utf8 " + escape(a) + " | utf8->latin1 " + escape(b)
                    + " | utf8->utf8 " + c + " " + (c.equals(name) ? "(intact)" : "(changed)"));
        }

        // Lossy encoding merges different users into one name
        String alice = "김민수", bob = "이서준";
        String aliceWire = receive(send(alice, StandardCharsets.ISO_8859_1), StandardCharsets.ISO_8859_1);
        String bobWire = receive(send(bob, StandardCharsets.ISO_8859_1), StandardCharsets.ISO_8859_1);
        System.out.println("noncompliant: two different users arrive as \"" + aliceWire + "\" and \"" + bobWire
                + "\", equal: " + aliceWire.equals(bobWire));

        // Compliant alternative: writeUTF/readUTF carry a length and a fixed encoding
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(wire)) {
            out.writeUTF(alice);
            out.writeUTF(bob);
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(wire.toByteArray()))) {
            String x = in.readUTF(), y = in.readUTF();
            System.out.println("compliant:    writeUTF/readUTF give \"" + x + "\" and \"" + y + "\", equal: " + x.equals(y));
        }
    }

    static byte[] send(String s, Charset cs) {
        return s.getBytes(cs); // unmappable characters become '?'
    }

    static String receive(byte[] bytes, Charset cs) {
        return new String(bytes, cs);
    }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            sb.append(ch < 0x7F && ch >= 0x20 ? String.valueOf(ch) : String.format("\\u%04X", (int) ch));
        }
        return sb.toString();
    }
}

/* Output:
José: latin1->utf8 Jos\uFFFD | utf8->latin1 Jos\u00C3\u00A9 | utf8->utf8 José (intact)
Ελένη: latin1->utf8 ????? | utf8->latin1 \u00CE\u0095\u00CE\u00BB\u00CE\u00AD\u00CE\u00BD\u00CE\u00B7 | utf8->utf8 Ελένη (intact)
김민수: latin1->utf8 ??? | utf8->latin1 \u00EA\u00B9\u0080\u00EB\u00AF\u00BC\u00EC\u0088\u0098 | utf8->utf8 김민수 (intact)
noncompliant: two different users arrive as "???" and "???", equal: true
compliant:    writeUTF/readUTF give "김민수" and "이서준", equal: false
*/
