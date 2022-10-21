// OBJ11-J. Be wary of letting constructors throw exceptions
// https://wiki.sei.cmu.edu/confluence/display/java/OBJ11-J.+Be+wary+of+letting+constructors+throw+exceptions
//
// When a constructor throws after Object's constructor has run, the object
// already exists and has been registered for finalization. A malicious
// subclass can override finalize(), which the garbage collector calls on the
// half-built instance, store "this" in a static field and then use an object
// whose security check failed (a "finalizer attack"). Defenses: make the
// class final; declare a final finalize() that does nothing; or (Java 6+)
// perform the check BEFORE Object's constructor runs, by evaluating it as an
// argument of a this(...) call, so no object is ever created. Finalization
// is deprecated for removal since Java 18, but still enabled by default.
public class OBJ11_ConstructorExceptions {
    public static void main(String[] args) throws InterruptedException {
        // Attack against the noncompliant class
        try {
            new EvilNoncompliant();
        } catch (SecurityException e) {
            System.out.println("noncompliant constructor threw: " + e.getMessage());
        }
        collectGarbage(() -> EvilNoncompliant.stolen != null);
        System.out.println("noncompliant: attacker captured an instance: " + (EvilNoncompliant.stolen != null));
        if (EvilNoncompliant.stolen != null) {
            System.out.println("noncompliant: " + EvilNoncompliant.stolen.transfer(1_000_000));
        }

        // Same attack against the compliant class
        try {
            new EvilCompliant();
        } catch (SecurityException e) {
            System.out.println("compliant constructor threw:    " + e.getMessage());
        }
        collectGarbage(() -> EvilCompliant.stolen != null);
        System.out.println("compliant: attacker captured an instance: " + (EvilCompliant.stolen != null));
    }

    // Encourage the GC and the finalizer thread to run (bounded wait)
    @SuppressWarnings("removal")
    static void collectGarbage(java.util.function.BooleanSupplier done) throws InterruptedException {
        for (int i = 0; i < 20 && !done.getAsBoolean(); i++) {
            System.gc();
            System.runFinalization();
            Thread.sleep(20);
        }
    }

    static boolean callerAuthorized() {
        return false; // the attacker is never authorized
    }
}

// Noncompliant: the check runs after Object() has completed
class BankNoncompliant {
    BankNoncompliant() {
        if (!OBJ11_ConstructorExceptions.callerAuthorized()) {
            throw new SecurityException("not authorized");
        }
    }

    String transfer(int amount) {
        return "transferred " + amount + " without authorization";
    }
}

class EvilNoncompliant extends BankNoncompliant {
    static volatile BankNoncompliant stolen;

    @SuppressWarnings({"removal", "deprecation"})
    @Override
    protected void finalize() {
        stolen = this; // resurrect the half-built object
    }
}

// Compliant: the check happens before Object's constructor, so there is
// nothing to finalize when it fails
class BankCompliant {
    BankCompliant() {
        this(securityCheck());
    }

    private BankCompliant(boolean checked) {
        // only reached when the check passed
    }

    private static boolean securityCheck() {
        if (!OBJ11_ConstructorExceptions.callerAuthorized()) {
            throw new SecurityException("not authorized");
        }
        return true;
    }

    String transfer(int amount) {
        return "transferred " + amount;
    }
}

class EvilCompliant extends BankCompliant {
    static volatile BankCompliant stolen;

    @SuppressWarnings({"removal", "deprecation"})
    @Override
    protected void finalize() {
        stolen = this;
    }
}

/* Output:
noncompliant constructor threw: not authorized
noncompliant: attacker captured an instance: true
noncompliant: transferred 1000000 without authorization
compliant constructor threw:    not authorized
compliant: attacker captured an instance: false
*/
