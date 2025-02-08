// Converting between bytes and text can fail in two ways:
//  - malformed input: the bytes are not valid in the charset (a lone 0xFF in
//    UTF-8, a truncated multi-byte sequence, an "overlong" encoding)
//  - unmappable character: the text has a character the charset cannot
//    represent (e.g. 'é' in US-ASCII)
// new String(bytes, cs) and String.getBytes(cs) never fail: they silently
// substitute U+FFFD or '?'. A CharsetDecoder / CharsetEncoder lets you choose
// per error type with CodingErrorAction:
//   REPORT  -> throw MalformedInputException / UnmappableCharacterException
//   REPLACE -> substitute the replacement ("�" or "?")
//   IGNORE  -> drop the offending input
// Use REPORT wherever corrupted or hostile input must be rejected instead of
// being "fixed" quietly. Files.readString already decodes strictly.
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

public class CharsetDecoderExample {
    static final HexFormat HEX = HexFormat.ofDelimiter(" ");

    static String decode(byte[] bytes, CodingErrorAction action) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(action)
                .onUnmappableCharacter(action);
        try {
            return show(decoder.decode(ByteBuffer.wrap(bytes)).toString());
        } catch (MalformedInputException e) {
            return "MalformedInputException (bad sequence of " + e.getInputLength() + " byte(s))";
        } catch (CharacterCodingException e) {
            return e.getClass().getSimpleName();
        }
    }

    static String encode(String text, Charset cs, CodingErrorAction action) {
        CharsetEncoder encoder = cs.newEncoder().onUnmappableCharacter(action);
        try {
            ByteBuffer out = encoder.encode(CharBuffer.wrap(text));
            byte[] bytes = new byte[out.remaining()];
            out.get(bytes);
            return HEX.formatHex(bytes);
        } catch (UnmappableCharacterException e) {
            return "UnmappableCharacterException (" + e.getInputLength() + " char(s))";
        } catch (CharacterCodingException e) {
            return e.getClass().getSimpleName();
        }
    }

    // Make U+FFFD and invisible characters visible in the output
    static String show(String s) {
        return '"' + s.replace("�", "<U+FFFD>").replace("﻿", "<BOM>") + '"';
    }

    public static void main(String[] args) throws Exception {
        byte[] valid = "naïve".getBytes(StandardCharsets.UTF_8);
        byte[] invalid = {'o', 'k', (byte) 0xFF, '!'};
        byte[] truncated = {'c', 'a', 'f', (byte) 0xC3};            // 'é' is C3 A9, the A9 is missing
        byte[] overlong = {'.', '.', (byte) 0xC0, (byte) 0xAF, 'x'}; // an overlong "/" (path tricks)
        System.out.println("valid UTF-8 bytes: " + HEX.formatHex(valid));

        System.out.println("-- decoding");
        System.out.println("new String(invalid):   " + show(new String(invalid, StandardCharsets.UTF_8)));
        for (CodingErrorAction action : new CodingErrorAction[] {
                CodingErrorAction.REPORT, CodingErrorAction.REPLACE, CodingErrorAction.IGNORE}) {
            System.out.println(String.format("%-8s", action) + "invalid:    " + decode(invalid, action));
            System.out.println(String.format("%-8s", action) + "truncated:  " + decode(truncated, action));
            System.out.println(String.format("%-8s", action) + "overlong:   " + decode(overlong, action));
        }

        System.out.println("-- encoding");
        String text = "café";
        System.out.println("getBytes(US_ASCII):     " + HEX.formatHex(text.getBytes(StandardCharsets.US_ASCII)) + "  ('?' is 3f)");
        System.out.println("REPORT  to US_ASCII:    " + encode(text, StandardCharsets.US_ASCII, CodingErrorAction.REPORT));
        System.out.println("REPLACE to US_ASCII:    " + encode(text, StandardCharsets.US_ASCII, CodingErrorAction.REPLACE));
        System.out.println("REPORT  to ISO_8859_1:  " + encode(text, StandardCharsets.ISO_8859_1, CodingErrorAction.REPORT));
        System.out.println("REPORT  to UTF_16BE:    " + encode(text, StandardCharsets.UTF_16BE, CodingErrorAction.REPORT));
        System.out.println("ASCII canEncode('é'): " + StandardCharsets.US_ASCII.newEncoder().canEncode('é')
                + ", ISO-8859-1 canEncode('€'): " + StandardCharsets.ISO_8859_1.newEncoder().canEncode('€'));

        System.out.println("-- charsets and BOMs");
        System.out.println("isSupported(\"windows-1252\"): " + Charset.isSupported("windows-1252")
                + ", aliases include cp1252: " + Charset.forName("windows-1252").aliases().contains("cp1252"));
        try {
            Charset.forName("no-such-charset");
        } catch (IllegalArgumentException e) { // UnsupportedCharsetException
            System.out.println("forName(\"no-such-charset\"): " + e.getClass().getSimpleName());
        }
        byte[] withBom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'h', 'i'};
        System.out.println("UTF-8 with BOM decodes to " + show(new String(withBom, StandardCharsets.UTF_8))
                + " (the BOM is kept)");
        System.out.println("UTF-16 with BOM FE FF: " + show(new String(new byte[] {(byte) 0xFE, (byte) 0xFF, 0, 'h', 0, 'i'},
                StandardCharsets.UTF_16)) + " (the BOM picks big-endian and is removed)");

        System.out.println("-- files");
        Path dir = Files.createTempDirectory("charsets");
        Path file = Files.write(dir.resolve("upload.txt"), invalid);
        try {
            System.out.println("new String(readAllBytes): " + show(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)));
            try {
                Files.readString(file);
            } catch (MalformedInputException e) {
                System.out.println("Files.readString:         MalformedInputException");
            }
        } finally {
            Files.delete(file);
            Files.delete(dir);
        }
    }
}

/* Output:
valid UTF-8 bytes: 6e 61 c3 af 76 65
-- decoding
new String(invalid):   "ok<U+FFFD>!"
REPORT  invalid:    MalformedInputException (bad sequence of 1 byte(s))
REPORT  truncated:  MalformedInputException (bad sequence of 1 byte(s))
REPORT  overlong:   MalformedInputException (bad sequence of 1 byte(s))
REPLACE invalid:    "ok<U+FFFD>!"
REPLACE truncated:  "caf<U+FFFD>"
REPLACE overlong:   "..<U+FFFD><U+FFFD>x"
IGNORE  invalid:    "ok!"
IGNORE  truncated:  "caf"
IGNORE  overlong:   "..x"
-- encoding
getBytes(US_ASCII):     63 61 66 3f  ('?' is 3f)
REPORT  to US_ASCII:    UnmappableCharacterException (1 char(s))
REPLACE to US_ASCII:    63 61 66 3f
REPORT  to ISO_8859_1:  63 61 66 e9
REPORT  to UTF_16BE:    00 63 00 61 00 66 00 e9
ASCII canEncode('é'): false, ISO-8859-1 canEncode('€'): false
-- charsets and BOMs
isSupported("windows-1252"): true, aliases include cp1252: true
forName("no-such-charset"): UnsupportedCharsetException
UTF-8 with BOM decodes to "<BOM>hi" (the BOM is kept)
UTF-16 with BOM FE FF: "hi" (the BOM picks big-endian and is removed)
-- files
new String(readAllBytes): "ok<U+FFFD>!"
Files.readString:         MalformedInputException
*/
