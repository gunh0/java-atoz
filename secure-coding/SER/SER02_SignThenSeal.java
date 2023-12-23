// SER02-J. Sign then seal objects before sending them across a trust boundary
// https://wiki.sei.cmu.edu/confluence/display/java/SER02-J.+Sign+then+seal+objects+before+sending+them+across+a+trust+boundary
//
// java.security.SignedObject protects a serialized object's integrity and
// javax.crypto.SealedObject its confidentiality. Using only one of them
// leaves the other property open, and the order matters when both are
// used. Seal-then-sign signs ciphertext: anyone on the path can strip the
// signature and sign the same sealed object with their own key, claiming
// authorship of content they never saw. Sign first, then seal the
// SignedObject: the signature is hidden inside the encryption and binds
// the signer to the plaintext. (Keys are generated in-process here; in
// practice they come from a key store.)
import java.io.Serializable;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignedObject;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SealedObject;
import javax.crypto.SecretKey;

public class SER02_SignThenSeal {
    static final String SIG = "SHA256withECDSA";

    public static void main(String[] args) throws Exception {
        KeyPair alice = ec();
        KeyPair mallory = ec();
        SecretKey shared = aes(); // Alice and Bob share this key; Mallory does not have it
        Order order = new Order("buy 100 ACME");

        // Signing only: anyone on the path can read the order
        SignedObject signedOnly = new SignedObject(order, alice.getPrivate(), Signature.getInstance(SIG));
        System.out.println("sign only, Mallory reads:    " + signedOnly.getObject());

        // Noncompliant: seal then sign; Mallory swaps Alice's signature for her own
        SignedObject fromAlice = new SignedObject(seal(order, shared), alice.getPrivate(), Signature.getInstance(SIG));
        SealedObject stolen = (SealedObject) fromAlice.getObject(); // no key needed to unwrap the signature layer
        SignedObject fromMallory = new SignedObject(stolen, mallory.getPrivate(), Signature.getInstance(SIG));
        System.out.println("noncompliant, Bob accepts:   " + receiveNoncompliant(fromMallory, mallory.getPublic(), shared)
                + " as Mallory's order");

        // Compliant: sign then seal; the signature is only reachable with the shared key
        SealedObject message = seal(new SignedObject(order, alice.getPrivate(), Signature.getInstance(SIG)), shared);
        System.out.println("compliant, Bob accepts:      " + receiveCompliant(message, alice.getPublic(), shared)
                + " as Alice's order");
        System.out.println("compliant, Mallory gets at the signature: " + tryUnseal(message, aes()));
    }

    // Bob verifies the outer signature against the claimed sender, then decrypts
    static Object receiveNoncompliant(SignedObject so, PublicKey sender, SecretKey key) throws Exception {
        if (!so.verify(sender, Signature.getInstance(SIG))) {
            return "rejected";
        }
        return ((SealedObject) so.getObject()).getObject(key);
    }

    static Object receiveCompliant(SealedObject sealed, PublicKey sender, SecretKey key) throws Exception {
        SignedObject so = (SignedObject) sealed.getObject(key);
        if (!so.verify(sender, Signature.getInstance(SIG))) {
            return "rejected";
        }
        return so.getObject();
    }

    static String tryUnseal(SealedObject sealed, SecretKey wrongKey) {
        try {
            sealed.getObject(wrongKey);
            return "yes";
        } catch (Exception e) {
            return "no, decryption fails without the shared key";
        }
    }

    static SealedObject seal(Serializable o, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return new SealedObject(o, cipher);
    }

    static KeyPair ec() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(256);
        return g.generateKeyPair();
    }

    static SecretKey aes() throws Exception {
        KeyGenerator g = KeyGenerator.getInstance("AES");
        g.init(256);
        return g.generateKey();
    }
}

record Order(String text) implements Serializable {}

/* Output:
sign only, Mallory reads:    Order[text=buy 100 ACME]
noncompliant, Bob accepts:   Order[text=buy 100 ACME] as Mallory's order
compliant, Bob accepts:      Order[text=buy 100 ACME] as Alice's order
compliant, Mallory gets at the signature: no, decryption fails without the shared key
*/
