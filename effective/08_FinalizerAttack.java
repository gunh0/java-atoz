import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class FinalizerAttack {
    public static void main(String[] args) {
        EvilFinalizer evil = new EvilFinalizer();

        // The "evil" object should fail to be created due to the constructor throwing
        // an exception,
        // but the object will not be destroyed until the finalize() method is executed.

        // Forcing the execution of the finalize() method of the "evil" object
        try {
            Method finalizeMethod = evil.getClass().getDeclaredMethod("finalize");
            finalizeMethod.setAccessible(true);
            finalizeMethod.invoke(evil);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }

        // Checking if the finalize() method of the "evil" object was executed
        System.out.println("Finalizer executed: " + EvilFinalizer.isFinalizerExecuted);
    }

    static class EvilFinalizer {
        public static boolean isFinalizerExecuted = false;

        @Override
        protected void finalize() throws Throwable {
            isFinalizerExecuted = true;
            System.out.println("EvilFinalizer's finalize() is executed");
            // Malicious code execution
            System.out.println("Malicious code executed!");
            // ...

            super.finalize();
        }
    }
}
