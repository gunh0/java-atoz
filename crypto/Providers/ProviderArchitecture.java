// JCA provider architecture: who actually implements Cipher.getInstance(...)
//
// The engine classes (MessageDigest, Cipher, Signature, ...) are only
// front ends. getInstance("SHA-256") asks java.security.Security for the
// registered providers in preference order and returns the first one that
// offers the algorithm; getInstance(name, provider) pins a provider. The
// list comes from the java.security file (security.provider.N entries), and
// Security.insertProviderAt can put e.g. a PKCS#11/HSM or FIPS provider in
// front at run time, which silently changes what every getInstance call in
// the JVM returns. That is why security reviews ask "which provider?" and why
// a FIPS deployment must also remove or restrict the default providers.
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.Signature;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.net.ssl.SSLContext;

public class ProviderArchitecture {
    public static void main(String[] args) throws Exception {
        // 1. Which provider wins for a few everyday requests?
        System.out.println("MessageDigest SHA-256        -> " + MessageDigest.getInstance("SHA-256").getProvider().getName());
        System.out.println("Mac HmacSHA256               -> " + Mac.getInstance("HmacSHA256").getProvider().getName());
        System.out.println("Cipher AES/GCM/NoPadding     -> " + Cipher.getInstance("AES/GCM/NoPadding").getProvider().getName());
        System.out.println("Signature SHA256withRSA      -> " + Signature.getInstance("SHA256withRSA").getProvider().getName());
        System.out.println("Signature SHA256withECDSA    -> " + Signature.getInstance("SHA256withECDSA").getProvider().getName());
        System.out.println("KeyAgreement X25519          -> " + KeyAgreement.getInstance("X25519").getProvider().getName());
        System.out.println("SSLContext TLSv1.3           -> " + SSLContext.getInstance("TLSv1.3").getProvider().getName());

        // 2. A provider is a Map of "Type.Algorithm" -> implementation class,
        //    plus aliases. "SHA256" is an alias of the standard name "SHA-256".
        Provider sun = Security.getProvider("SUN");
        Provider.Service svc = sun.getService("MessageDigest", "SHA-256");
        System.out.println();
        System.out.println("SUN offers MessageDigest.SHA-256: " + (svc != null));
        // getService resolves aliases to the service's standard name
        System.out.println("alias SHA256 resolves to: " + sun.getService("MessageDigest", "SHA256").getAlgorithm());

        // 3. Ask for an algorithm nobody provides: the error names the request,
        //    it never falls back to something "close enough".
        try {
            MessageDigest.getInstance("SHA-257");
        } catch (NoSuchAlgorithmException e) {
            System.out.println("SHA-257: " + e.getClass().getSimpleName());
        }

        // 4. Pinning a provider: SunJCE has AES, SUN does not.
        try {
            Cipher.getInstance("AES/GCM/NoPadding", "SUN");
        } catch (NoSuchAlgorithmException e) {
            System.out.println("AES from SUN: " + e.getClass().getSimpleName());
        }

        // 5. The standard providers we rely on are all installed (the full list
        //    and its order is platform-specific, so only membership is printed).
        System.out.println();
        for (String name : List.of("SUN", "SunRsaSign", "SunEC", "SunJSSE", "SunJCE")) {
            Provider p = Security.getProvider(name);
            System.out.printf("%-10s installed: %b%n", name, p != null);
        }
        System.out.println("SUN is preferred over SunJCE: " + (position("SUN") < position("SunJCE")));
    }

    static int position(String name) {
        Provider[] all = Security.getProviders();
        for (int i = 0; i < all.length; i++) {
            if (all[i].getName().equals(name)) return i + 1; // positions are 1-based
        }
        return -1;
    }
}

/* Output:
MessageDigest SHA-256        -> SUN
Mac HmacSHA256               -> SunJCE
Cipher AES/GCM/NoPadding     -> SunJCE
Signature SHA256withRSA      -> SunRsaSign
Signature SHA256withECDSA    -> SunEC
KeyAgreement X25519          -> SunEC
SSLContext TLSv1.3           -> SunJSSE

SUN offers MessageDigest.SHA-256: true
alias SHA256 resolves to: SHA-256
SHA-257: NoSuchAlgorithmException
AES from SUN: NoSuchAlgorithmException

SUN        installed: true
SunRsaSign installed: true
SunEC      installed: true
SunJSSE    installed: true
SunJCE     installed: true
SUN is preferred over SunJCE: true
*/
