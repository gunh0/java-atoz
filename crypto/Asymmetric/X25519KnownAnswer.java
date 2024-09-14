// X25519 (XDH) key agreement, checked against RFC 7748
//
// X25519 is Diffie-Hellman on Curve25519 in Montgomery form, and since Java
// 11 it is KeyAgreement "X25519" (also "X448"). Compared with ECDH on P-256
// it is designed to be hard to misuse: keys are plain 32-byte strings,
// every 32-byte string is a valid public key (no point validation to forget:
// the curve and its twist are both secure), and the private scalar is
// "clamped" (bits fixed) so implementations cannot leak bits through the
// small cofactor. Java models a public key as the u-coordinate (a
// BigInteger) and a private key as the 32 scalar bytes. Wire format is
// little-endian, so converting the RFC's bytes into a BigInteger means
// reversing them. As with any DH, feed the result into a KDF.
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPrivateKeySpec;
import java.security.spec.XECPublicKeySpec;
import java.util.HexFormat;
import javax.crypto.KeyAgreement;

public class X25519KnownAnswer {
    static final HexFormat HEX = HexFormat.of();

    public static void main(String[] args) throws Exception {
        // RFC 7748, section 6.1
        String alicePriv = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a";
        String alicePub = "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a";
        String bobPriv = "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb";
        String bobPub = "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f";
        String shared = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742";

        KeyFactory kf = KeyFactory.getInstance("XDH");
        PrivateKey a = kf.generatePrivate(new XECPrivateKeySpec(NamedParameterSpec.X25519, HEX.parseHex(alicePriv)));
        PrivateKey b = kf.generatePrivate(new XECPrivateKeySpec(NamedParameterSpec.X25519, HEX.parseHex(bobPriv)));
        PublicKey aPub = publicKey(kf, alicePub);
        PublicKey bPub = publicKey(kf, bobPub);

        byte[] kA = agree(a, bPub);
        byte[] kB = agree(b, aPub);
        System.out.println("alice computes K: " + (HEX.formatHex(kA).equals(shared) ? "OK" : "MISMATCH"));
        System.out.println("bob computes K:   " + (HEX.formatHex(kB).equals(shared) ? "OK" : "MISMATCH"));

        // Fresh key pair: the u-coordinate round-trips through the little-endian encoding
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
        XECPublicKey fresh = (XECPublicKey) kpg.generateKeyPair().getPublic();
        String wire = toWire(fresh.getU());
        System.out.println("public key: " + wire.length() / 2 + " bytes on the wire, X.509 encoding "
                + fresh.getEncoded().length + " bytes, round trip: " + ((XECPublicKey) publicKey(kf, wire)).getU().equals(fresh.getU()));
    }

    // RFC 7748 public keys are little-endian u-coordinates
    static PublicKey publicKey(KeyFactory kf, String hexLE) throws Exception {
        byte[] le = HEX.parseHex(hexLE);
        byte[] be = new byte[le.length];
        for (int i = 0; i < le.length; i++) be[i] = le[le.length - 1 - i];
        return kf.generatePublic(new XECPublicKeySpec(NamedParameterSpec.X25519, new BigInteger(1, be)));
    }

    static String toWire(BigInteger u) {
        byte[] le = new byte[32];
        byte[] be = u.toByteArray();
        for (int i = 0; i < 32 && i < be.length; i++) le[i] = be[be.length - 1 - i];
        return HEX.formatHex(le);
    }

    static byte[] agree(PrivateKey mine, PublicKey theirs) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance("X25519");
        ka.init(mine);
        ka.doPhase(theirs, true);
        return ka.generateSecret();
    }
}

/* Output:
alice computes K: OK
bob computes K:   OK
public key: 32 bytes on the wire, X.509 encoding 44 bytes, round trip: true
*/
