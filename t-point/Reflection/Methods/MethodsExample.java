// java.lang.reflect.Method describes one method:
//  - getDeclaredMethods(): methods declared in this class, any access level
//    (unordered; may include compiler-generated synthetic methods such as
//    lambda bodies, filter them with isSynthetic())
//  - getMethods(): public methods, including inherited ones
//  - getMethod(name, paramTypes...) looks one up by exact signature
//  - invoke(target, args...) calls it; target is null for static methods
// Exceptions thrown by the invoked method arrive wrapped in
// InvocationTargetException; unwrap with getCause().
// Below, a tiny command shell maps typed command names to methods.
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class MethodsExample {
    static class AdminShell {
        private int blocked = 0;

        public String cmdBlock(String ip) {
            blocked++;
            return "blocked " + ip + " (" + blocked + " total)";
        }

        public String cmdStatus() {
            return "blocked=" + blocked;
        }

        public String cmdUnblock(String ip) {
            throw new IllegalArgumentException(ip + " is not blocked");
        }

        private String wipeLogs() {
            return "logs wiped";
        }

        public static String version() {
            return "shell 1.0";
        }

        Runnable hook() {
            return () -> {}; // compiles to a synthetic lambda method
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("declared methods:");
        Method[] methods = AdminShell.class.getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));
        for (Method m : methods) {
            if (m.isSynthetic()) continue; // hide compiler-generated lambda$... methods
            String params = Arrays.stream(m.getParameterTypes()).map(Class::getSimpleName).collect(Collectors.joining(", "));
            System.out.printf("  %-14s %s(%s) -> %s%n", Modifier.toString(m.getModifiers()), m.getName(), params,
                    m.getReturnType().getSimpleName());
        }
        long synthetic = Arrays.stream(methods).filter(Method::isSynthetic).count();
        System.out.println("synthetic methods hidden: " + synthetic);

        List<String> fromObject = Arrays.stream(AdminShell.class.getMethods())
                .filter(m -> m.getDeclaringClass() == Object.class).map(Method::getName).distinct().sorted().toList();
        System.out.println("public methods inherited from Object: " + fromObject);

        // Dispatch commands by name. Only public methods prefixed with "cmd" are allowed,
        // so a user cannot reach wipeLogs() or anything else by typing its name.
        AdminShell shell = new AdminShell();
        for (String line : List.of("block 10.0.0.5", "block 10.0.0.9", "status", "unblock 10.0.0.1", "wipeLogs", "reboot")) {
            System.out.println("> " + line);
            System.out.println("  " + dispatch(shell, line));
        }

        // Static method: no target object
        Method version = AdminShell.class.getMethod("version");
        System.out.println("static invoke: " + version.invoke(null));

        // Private method: getMethod cannot see it, getDeclaredMethod + setAccessible can
        Method wipe = AdminShell.class.getDeclaredMethod("wipeLogs");
        wipe.setAccessible(true);
        System.out.println("private invoke: " + wipe.invoke(shell));
    }

    static String dispatch(Object target, String line) throws IllegalAccessException {
        String[] parts = line.split(" ");
        String name = "cmd" + Character.toUpperCase(parts[0].charAt(0)) + parts[0].substring(1);
        Object[] cmdArgs = Arrays.copyOfRange(parts, 1, parts.length);
        Class<?>[] types = new Class<?>[cmdArgs.length];
        Arrays.fill(types, String.class);
        try {
            Method m = target.getClass().getMethod(name, types);
            return (String) m.invoke(target, cmdArgs);
        } catch (NoSuchMethodException e) {
            return "unknown command: " + parts[0];
        } catch (InvocationTargetException e) {
            // The command itself threw; the real exception is the cause
            return "command failed: " + e.getCause().getMessage();
        }
    }
}

/* Output:
declared methods:
  public         cmdBlock(String) -> String
  public         cmdStatus() -> String
  public         cmdUnblock(String) -> String
                 hook() -> Runnable
  public static  version() -> String
  private        wipeLogs() -> String
synthetic methods hidden: 1
public methods inherited from Object: [equals, getClass, hashCode, notify, notifyAll, toString, wait]
> block 10.0.0.5
  blocked 10.0.0.5 (1 total)
> block 10.0.0.9
  blocked 10.0.0.9 (2 total)
> status
  blocked=2
> unblock 10.0.0.1
  command failed: 10.0.0.1 is not blocked
> wipeLogs
  unknown command: wipeLogs
> reboot
  unknown command: reboot
static invoke: shell 1.0
private invoke: logs wiped
*/
