// The ECB penguin: why a block cipher alone does not hide patterns
//
// ECB mode encrypts each 16-byte block independently with the same key, so
// equal plaintext blocks give equal ciphertext blocks. Images, database
// columns and structured records are full of repeated blocks, and their
// shape survives encryption (the famous Tux picture encrypted with ECB is
// still a recognizable penguin). Modes with an IV or nonce (CBC, CTR, GCM)
// chain or offset every block so repetitions disappear. Below, a small
// "bitmap" whose pixels are 16-byte blocks is encrypted with ECB and with
// GCM; each distinct ciphertext block is then drawn with its own symbol, in
// order of first appearance, which is all an eavesdropper needs.
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class EcbPenguin {
    static final String[] IMAGE = {
        "......##......",
        "....######....",
        "...##o##o##...",
        "...########...",
        "..##########..",
        "..##..##..##..",
        "......##......",
    };

    public static void main(String[] args) throws Exception {
        // One pixel = one 16-byte block: '.' -> block of 0x00, '#' -> 0xff, 'o' -> 0x80
        int w = IMAGE[0].length(), h = IMAGE.length;
        byte[] pixels = new byte[w * h * 16];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                byte v = switch (IMAGE[y].charAt(x)) { case '#' -> (byte) 0xff; case 'o' -> (byte) 0x80; default -> 0; };
                Arrays.fill(pixels, (y * w + x) * 16, (y * w + x + 1) * 16, v);
            }
        }
        SecretKey key = KeyGenerator.getInstance("AES").generateKey();

        Cipher ecb = Cipher.getInstance("AES/ECB/NoPadding");
        ecb.init(Cipher.ENCRYPT_MODE, key);
        render("AES/ECB", ecb.doFinal(pixels), w, h);

        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
        gcm.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        render("AES/GCM", gcm.doFinal(pixels), w, h); // trailing 16-byte tag is ignored
    }

    // Draw the ciphertext: same block -> same symbol (only up to 5 symbols, then '?')
    static void render(String label, byte[] ct, int w, int h) {
        Map<String, Character> symbols = new HashMap<>();
        String palette = "ABCDE";
        StringBuilder art = new StringBuilder();
        for (int y = 0; y < h; y++) {
            art.append("  ");
            for (int x = 0; x < w; x++) {
                String block = HexFormat.of().formatHex(ct, (y * w + x) * 16, (y * w + x + 1) * 16);
                char s = symbols.computeIfAbsent(block,
                        b -> symbols.size() < palette.length() ? palette.charAt(symbols.size()) : '?');
                art.append(s);
            }
            art.append('\n');
        }
        System.out.println(label + ": " + w * h + " blocks, " + symbols.size() + " distinct ciphertext blocks");
        System.out.print(symbols.size() <= palette.length() ? art : "  (every block different: no picture)\n");
    }
}

/* Output:
AES/ECB: 98 blocks, 3 distinct ciphertext blocks
  AAAAAABBAAAAAA
  AAAABBBBBBAAAA
  AAABBCBBCBBAAA
  AAABBBBBBBBAAA
  AABBBBBBBBBBAA
  AABBAABBAABBAA
  AAAAAABBAAAAAA
AES/GCM: 98 blocks, 98 distinct ciphertext blocks
  (every block different: no picture)
*/
