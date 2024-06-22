// DCL00-J. Prevent class initialization cycles
// https://wiki.sei.cmu.edu/confluence/display/java/DCL00-J.+Prevent+class+initialization+cycles
//
// Static initializers run once, top to bottom, when a class is first
// used. If an initializer (directly, or through another class) uses a
// static field that has not been initialized yet, it silently reads the
// default value (0, false, null) instead of failing. The classic form is a
// static instance created before the constants its constructor needs, or
// two classes whose static fields refer to each other. The result depends
// on declaration order, or on which class happens to be touched first.
// Order static fields so every one is initialized before it is used, and
// break cycles between classes.
public class DCL00_ClassInitializationCycles {
    public static void main(String[] args) {
        System.out.println("noncompliant: Account.INSTANCE.balance = " + AccountNoncompliant.INSTANCE.balance
                + " (expected " + (AccountNoncompliant.DEPOSIT - 10) + ")");
        System.out.println("compliant:    Account.INSTANCE.balance = " + AccountCompliant.INSTANCE.balance);

        // Two classes that read each other's statics: the result depends on which is used first
        System.out.println("noncompliant: Limits.MAX_SESSIONS = " + Limits.MAX_SESSIONS
                + ", Policy.SESSION_CAP = " + Policy.SESSION_CAP);
        System.out.println("compliant:    Config.MAX_SESSIONS = " + Config.MAX_SESSIONS
                + ", Config.SESSION_CAP = " + Config.SESSION_CAP);
    }
}

class AccountNoncompliant {
    // The instance is created while DEPOSIT is still 0
    static final AccountNoncompliant INSTANCE = new AccountNoncompliant();
    static final int DEPOSIT = Integer.parseInt("100"); // not a compile-time constant
    final int balance;

    AccountNoncompliant() {
        balance = DEPOSIT - 10;
    }
}

class AccountCompliant {
    static final int DEPOSIT = Integer.parseInt("100");
    static final AccountCompliant INSTANCE = new AccountCompliant(); // after its dependencies
    final int balance;

    AccountCompliant() {
        balance = DEPOSIT - 10;
    }
}

// Noncompliant: a cycle between two classes
class Limits {
    static final int MAX_SESSIONS = Policy.SESSION_CAP + 1;
    static final int DEFAULT = Integer.parseInt("50");
}

class Policy {
    // Initialized while Limits is still initializing, so Limits.DEFAULT reads 0
    static final int SESSION_CAP = Limits.DEFAULT * 2;
}

// Compliant: one class owns the values, in dependency order
class Config {
    static final int DEFAULT = Integer.parseInt("50");
    static final int SESSION_CAP = DEFAULT * 2;
    static final int MAX_SESSIONS = SESSION_CAP + 1;
}

/* Output:
noncompliant: Account.INSTANCE.balance = -10 (expected 90)
compliant:    Account.INSTANCE.balance = 90
noncompliant: Limits.MAX_SESSIONS = 1, Policy.SESSION_CAP = 0
compliant:    Config.MAX_SESSIONS = 101, Config.SESSION_CAP = 100
*/
