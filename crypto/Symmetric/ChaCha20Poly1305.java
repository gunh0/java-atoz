// ChaCha20-Poly1305: the other AEAD (RFC 8439), checked against its vector
//
// ChaCha20 is a stream cipher built only from 32-bit additions, rotations
// and XORs, so it is fast and constant-time in plain software, without the
// AES instructions that make AES-GCM fast (phones, small devices). Poly1305
// authenticates the ciphertext and AAD. TLS 1.3, WireGuard and SSH offer it
// next to AES-GCM. Same contract as GCM: 256-bit key, 96-bit nonce never
// reused under a key, 128-bit tag appended to the ciphertext. In Java
// (since 11) the nonce goes in an IvParameterSpec, and like GCM the cipher
// refuses to encrypt twice with the same key and nonce.
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.HexFormat;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class ChaCha20Poly1305 {
    public static void main(String[] args) throws Exception {
        HexFormat hex = HexFormat.of();
        // RFC 8439, section 2.8.2
        SecretKeySpec key = new SecretKeySpec(hex.parseHex(
                "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f"), "ChaCha20");
        byte[] nonce = hex.parseHex("070000004041424344454647");
        byte[] aad = hex.parseHex("50515253c0c1c2c3c4c5c6c7");
        byte[] plaintext = ("Ladies and Gentlemen of the class of '99: If I could offer you only one tip "
                + "for the future, sunscreen would be it.").getBytes(StandardCharsets.US_ASCII);

        Cipher enc = Cipher.getInstance("ChaCha20-Poly1305");
        enc.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(nonce));
        enc.updateAAD(aad);
        byte[] out = enc.doFinal(plaintext);
        String ctHex = hex.formatHex(out, 0, plaintext.length);
        String tagHex = hex.formatHex(out, plaintext.length, out.length);
        System.out.println("plaintext " + plaintext.length + " bytes -> " + out.length + " bytes (ciphertext + 16-byte tag)");
        System.out.println("ciphertext starts d31a8d34648e60db: " + ctHex.startsWith("d31a8d34648e60db"));
        System.out.println("tag " + tagHex + " " + (tagHex.equals("1ae10b594f09e26a7e902ecbd0600691") ? "OK" : "MISMATCH"));

        // Decrypt, then tamper with the AAD
        System.out.println("decrypts: " + open(key, nonce, aad, out).startsWith("Ladies and Gentlemen"));
        byte[] otherAad = aad.clone();
        otherAad[0] ^= 1;
        System.out.println("other AAD: " + open(key, nonce, otherAad, out));

        // Nonce-reuse guard, as with GCM
        try {
            enc.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(nonce));
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            System.out.println("re-init with the same key and nonce: " + e.getClass().getSimpleName());
        }
    }

    static String open(SecretKeySpec key, byte[] nonce, byte[] aad, byte[] ct) throws Exception {
        Cipher dec = Cipher.getInstance("ChaCha20-Poly1305");
        dec.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(nonce));
        dec.updateAAD(aad);
        try {
            return new String(dec.doFinal(ct), StandardCharsets.US_ASCII);
        } catch (AEADBadTagException e) {
            return "rejected (" + e.getClass().getSimpleName() + ")";
        }
    }
}

/* Output:
plaintext 114 bytes -> 130 bytes (ciphertext + 16-byte tag)
ciphertext starts d31a8d34648e60db: true
tag 1ae10b594f09e26a7e902ecbd0600691 OK
decrypts: true
other AAD: rejected (AEADBadTagException)
re-init with the same key and nonce: InvalidKeyException
*/
