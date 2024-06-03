// AES as a block cipher, checked against FIPS 197 Appendix C
//
// AES is a keyed permutation on 16-byte blocks: with the key, every block
// maps to exactly one other block and back. That is all it is. On its own
// it is NOT an encryption scheme for messages: you need a mode (GCM, CTR,
// CBC...) that handles many blocks, randomizes with an IV/nonce and, ideally,
// authenticates. "AES/ECB/NoPadding" applies the raw permutation block by
// block, which is exactly right for a known-answer test of one block and
// almost always wrong for data: equal blocks stay equal. Key sizes 128, 192
// and 256 bits run 10, 12 and 14 rounds; all three are fine today, 256
// is the conservative choice against future (quantum) search.
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class AesKnownAnswer {
    static final HexFormat HEX = HexFormat.of();
    static final byte[] PLAINTEXT = HEX.parseHex("00112233445566778899aabbccddeeff");

    public static void main(String[] args) throws Exception {
        kat("AES-128", "000102030405060708090a0b0c0d0e0f", "69c4e0d86a7b0430d8cdb78070b4c55a");
        kat("AES-192", "000102030405060708090a0b0c0d0e0f1011121314151617", "dda97ca4864cdfe06eaf70a0ec0d7191");
        kat("AES-256", "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f", "8ea2b7ca516745bfeafc49904b496089");

        // A permutation: same key + same block -> same output, every time.
        // That determinism is what modes must hide.
        Cipher c = Cipher.getInstance("AES/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(HEX.parseHex("000102030405060708090a0b0c0d0e0f"), "AES"));
        byte[] twoBlocks = new byte[32];
        System.arraycopy(PLAINTEXT, 0, twoBlocks, 0, 16);
        System.arraycopy(PLAINTEXT, 0, twoBlocks, 16, 16);
        byte[] out = c.doFinal(twoBlocks);
        System.out.println("two equal blocks encrypt to equal blocks: "
                + HEX.formatHex(out, 0, 16).equals(HEX.formatHex(out, 16, 32)));

        // NoPadding means input must be a whole number of blocks
        try {
            c.doFinal(new byte[15]);
        } catch (javax.crypto.IllegalBlockSizeException e) {
            System.out.println("15 bytes with NoPadding: " + e.getClass().getSimpleName());
        }
        System.out.println("block size: " + c.getBlockSize() + " bytes");
    }

    static void kat(String label, String keyHex, String expectedHex) throws Exception {
        SecretKeySpec key = new SecretKeySpec(HEX.parseHex(keyHex), "AES");
        Cipher enc = Cipher.getInstance("AES/ECB/NoPadding");
        enc.init(Cipher.ENCRYPT_MODE, key);
        byte[] ct = enc.doFinal(PLAINTEXT);
        Cipher dec = Cipher.getInstance("AES/ECB/NoPadding");
        dec.init(Cipher.DECRYPT_MODE, key);
        boolean roundTrip = HEX.formatHex(dec.doFinal(ct)).equals(HEX.formatHex(PLAINTEXT));
        System.out.println(label + ": " + HEX.formatHex(ct) + " " + (HEX.formatHex(ct).equals(expectedHex) ? "OK" : "MISMATCH")
                + ", decrypts back: " + roundTrip);
    }
}

/* Output:
AES-128: 69c4e0d86a7b0430d8cdb78070b4c55a OK, decrypts back: true
AES-192: dda97ca4864cdfe06eaf70a0ec0d7191 OK, decrypts back: true
AES-256: 8ea2b7ca516745bfeafc49904b496089 OK, decrypts back: true
two equal blocks encrypt to equal blocks: true
15 bytes with NoPadding: IllegalBlockSizeException
block size: 16 bytes
*/
