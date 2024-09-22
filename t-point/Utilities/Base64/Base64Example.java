// java.util.Base64 encodes bytes as printable ASCII: 3 bytes -> 4 chars.
// Three flavours, each with an Encoder and a Decoder:
//  - getEncoder(): RFC 4648 basic alphabet A-Z a-z 0-9 + /, no line breaks
//  - getUrlEncoder(): - and _ instead of + and /, safe in URLs, file names
//    and JWTs (usually combined with withoutPadding())
//  - getMimeEncoder(): lines of at most 76 chars separated by CRLF
// Decoders reject characters outside their alphabet (IllegalArgumentException);
// the MIME decoder ignores them instead. Base64 is an encoding, not
// encryption: anyone can decode it.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

public class Base64Example {
    public static void main(String[] args) throws IOException {
        // Padding: 1, 2 and 3 input bytes
        for (String s : new String[] {"M", "Ma", "Man"}) {
            System.out.println(s + " -> " + Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.US_ASCII)));
        }

        // Basic vs URL-safe on bytes that hit + and /
        byte[] data = {(byte) 0xfb, (byte) 0xff, (byte) 0xbf, 0x3e};
        System.out.println("basic:            " + Base64.getEncoder().encodeToString(data));
        System.out.println("url:              " + Base64.getUrlEncoder().encodeToString(data));
        System.out.println("url, no padding:  " + Base64.getUrlEncoder().withoutPadding().encodeToString(data));

        // Round trip, padding optional for decoding
        byte[] back = Base64.getUrlDecoder().decode("-_-_Pg");
        System.out.println("decoded unpadded: " + Arrays.equals(data, back));

        // Credentials in an HTTP Basic header are only encoded
        String header = "Basic " + Base64.getEncoder().encodeToString("alice:s3cret".getBytes(StandardCharsets.UTF_8));
        System.out.println("header: " + header);
        String decoded = new String(Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8);
        System.out.println("anyone can decode: " + decoded);

        // Wrong alphabet or garbage: the basic decoder throws
        for (String input : new String[] {"-_-_Pg", "abc$", "abcde"}) {
            try {
                Base64.getDecoder().decode(input);
                System.out.println("basic decoder accepted " + input);
            } catch (IllegalArgumentException e) {
                System.out.println("basic decoder rejected " + input);
            }
        }

        // MIME: 76-char lines with CRLF; the MIME decoder skips line breaks
        byte[] blob = new byte[100];
        for (int i = 0; i < blob.length; i++) {
            blob[i] = (byte) i;
        }
        String mime = Base64.getMimeEncoder().encodeToString(blob);
        String[] lines = mime.split("\r\n");
        System.out.println("mime lines: " + lines.length + ", first length " + lines[0].length());
        System.out.println("mime round trip: " + Arrays.equals(blob, Base64.getMimeDecoder().decode(mime)));
        System.out.println("custom line length 20: "
                + Base64.getMimeEncoder(20, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(blob).split("\n").length + " lines");

        // Streaming: wrap an OutputStream / InputStream
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (OutputStream out = Base64.getEncoder().wrap(sink)) {
            out.write("streamed ".getBytes(StandardCharsets.US_ASCII));
            out.write("content".getBytes(StandardCharsets.US_ASCII));
        } // close() writes the final padding
        String encoded = sink.toString(StandardCharsets.US_ASCII);
        System.out.println("wrapped encoder: " + encoded);
        try (InputStream in = Base64.getDecoder().wrap(new ByteArrayInputStream(encoded.getBytes(StandardCharsets.US_ASCII)))) {
            System.out.println("wrapped decoder: " + new String(in.readAllBytes(), StandardCharsets.US_ASCII));
        }
    }
}

/* Output:
M -> TQ==
Ma -> TWE=
Man -> TWFu
basic:            +/+/Pg==
url:              -_-_Pg==
url, no padding:  -_-_Pg
decoded unpadded: true
header: Basic YWxpY2U6czNjcmV0
anyone can decode: alice:s3cret
basic decoder rejected -_-_Pg
basic decoder rejected abc$
basic decoder rejected abcde
mime lines: 2, first length 76
mime round trip: true
custom line length 20: 7 lines
wrapped encoder: c3RyZWFtZWQgY29udGVudA==
wrapped decoder: streamed content
*/
