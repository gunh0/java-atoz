// JEP 486 — Permanently Disable the Security Manager (Java 24)
//
// The Security Manager dates from applets: a policy file granted
// permissions to code by origin, and every sensitive JDK operation called
// checkPermission and walked the stack. On the server it was almost never
// used, hard to configure correctly (one overly broad grant undid it), and
// it cost every JDK feature extra code paths. JEP 411 (Java 17) deprecated it
// for removal; Java 18 made enabling it at run time opt-in
// (-Djava.security.manager=allow). Java 24 removes the ability to enable it
// at all: System.setSecurityManager always throws
// UnsupportedOperationException, getSecurityManager always returns null, and
// a JVM started with -Djava.security.manager (or =allow, =default, or a
// class name) refuses to start; only =disallow is accepted. The API classes
// (SecurityManager, AccessController, Policy, ...) remain for compatibility
// and behave as if no manager were installed. What replaces it for
// untrusted code is isolation outside the JVM (processes, containers, OS
// sandboxing) plus in-JVM measures such as strong module encapsulation
// (java17/StrongEncapsulation.java) and deserialization filters.
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;

public class SecurityManagerDisabled {
    @SuppressWarnings("removal")
    public static void main(String[] args) throws Exception {
        System.out.println("System.getSecurityManager(): " + System.getSecurityManager());
        try {
            System.setSecurityManager(new SecurityManager());
            System.out.println("Security Manager installed?!");
        } catch (UnsupportedOperationException e) {
            System.out.println("System.setSecurityManager(...): UnsupportedOperationException");
        }

        // Legacy privileged blocks still compile and simply run the action
        String result = AccessController.doPrivileged((PrivilegedAction<String>) () -> "ran without any permission check");
        System.out.println("AccessController.doPrivileged: " + result);

        // Enabling it from the command line is no longer possible
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        for (String option : new String[] {"-Djava.security.manager", "-Djava.security.manager=allow",
                "-Djava.security.manager=default", "-Djava.security.manager=disallow"}) {
            Process p = new ProcessBuilder(java, option, "-version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            System.out.printf("java %-32s -> JVM starts: %s%n", option, p.waitFor() == 0);
        }
    }
}

/* Output:
System.getSecurityManager(): null
System.setSecurityManager(...): UnsupportedOperationException
AccessController.doPrivileged: ran without any permission check
java -Djava.security.manager          -> JVM starts: false
java -Djava.security.manager=allow    -> JVM starts: false
java -Djava.security.manager=default  -> JVM starts: false
java -Djava.security.manager=disallow -> JVM starts: true
*/
