// OBJ07-J. Sensitive classes must not let themselves be copied
// https://wiki.sei.cmu.edu/confluence/display/java/OBJ07-J.+Sensitive+classes+must+not+let+themselves+be+copied
//
// A class that guards a secret or enforces "use once" semantics relies on
// there being exactly one instance with that state. If the class is not
// final, an attacker can subclass it, implement Cloneable, and call
// Object.clone() through super.clone(): the copy is made field by field
// without running any constructor, so it duplicates the secret and the
// usage counter, and wiping or consuming the original does not affect the
// copy. Make sensitive classes final, or, if they must be extensible,
// override clone() as final and make it throw CloneNotSupportedException.
// (Serialization is another copy mechanism; see the SER rules.)
public class OBJ07_SensitiveClassCopy {
    public static void main(String[] args) {
        // Noncompliant: an attacker's subclass clones a one-time token
        TokenNoncompliant token = new CloningAttacker("otp-482913");
        CloningAttacker copy = ((CloningAttacker) token).duplicate();
        System.out.println("noncompliant: original redeemed " + token.redeem() + ", again " + token.redeem());
        System.out.println("noncompliant: copy redeemed     " + copy.redeem() + " (same one-time code used twice)");

        // Compliant: the subclass cannot obtain a copy
        TokenCompliant safe = new CloningAttackerCompliant("otp-482913");
        try {
            ((CloningAttackerCompliant) safe).duplicate();
        } catch (CloneNotSupportedException e) {
            System.out.println("compliant:    clone refused (" + e.getMessage() + ")");
        }
        System.out.println("compliant:    redeemed " + safe.redeem() + ", again " + safe.redeem());
    }
}

// Noncompliant: not final, no clone() of its own
class TokenNoncompliant {
    private final String code;
    private boolean used;

    TokenNoncompliant(String code) {
        this.code = code;
    }

    String redeem() {
        if (used) {
            return "rejected";
        }
        used = true;
        return code;
    }
}

class CloningAttacker extends TokenNoncompliant implements Cloneable {
    CloningAttacker(String code) {
        super(code);
    }

    CloningAttacker duplicate() {
        try {
            return (CloningAttacker) super.clone(); // copies private fields too
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}

// Compliant: subclassable for some reason, but clone() is final and refuses
class TokenCompliant {
    private final String code;
    private boolean used;

    TokenCompliant(String code) {
        this.code = code;
    }

    @Override
    protected final Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException(getClass().getSimpleName() + " must not be copied");
    }

    String redeem() {
        if (used) {
            return "rejected";
        }
        used = true;
        return code;
    }
}

class CloningAttackerCompliant extends TokenCompliant implements Cloneable {
    CloningAttackerCompliant(String code) {
        super(code);
    }

    Object duplicate() throws CloneNotSupportedException {
        return super.clone();
    }
}

/* Output:
noncompliant: original redeemed otp-482913, again rejected
noncompliant: copy redeemed     otp-482913 (same one-time code used twice)
compliant:    clone refused (CloningAttackerCompliant must not be copied)
compliant:    redeemed otp-482913, again rejected
*/
