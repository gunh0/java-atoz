// java.lang.reflect.Constructor creates objects of a class known only at run
// time, which is how plugin systems, dependency injection and deserializers
// work:
//   Class.forName(name).asSubclass(Plugin.class)    check the type first
//       .getDeclaredConstructor(paramTypes...)      pick a constructor
//       .newInstance(args...)                       call it
// (Class.newInstance() is deprecated: it rethrows checked exceptions
// unchecked.) Reflection can call private constructors too, which is why a
// private constructor alone does not make a singleton safe; enum constants
// cannot be created reflectively at all.
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ConstructorsExample {
    interface Scanner {
        String scan(String target);
    }

    public static class PortScanner implements Scanner {
        private final int maxPort;

        public PortScanner() { this(1024); }
        public PortScanner(int maxPort) {
            if (maxPort < 1 || maxPort > 65535) throw new IllegalArgumentException("bad port " + maxPort);
            this.maxPort = maxPort;
        }
        public String scan(String target) { return "port scan of " + target + " up to " + maxPort; }
    }

    public static class TlsScanner implements Scanner {
        public String scan(String target) { return "TLS check of " + target; }
    }

    static final class Vault {
        static final Vault INSTANCE = new Vault();
        private Vault() {}
    }

    enum Mode { STRICT, LENIENT }

    record Finding(String host, int severity) {}

    public static void main(String[] args) throws Exception {
        // List constructors (sorted by parameter count; reflection order is unspecified)
        Constructor<?>[] ctors = PortScanner.class.getConstructors();
        Arrays.sort(ctors, Comparator.comparingInt(Constructor::getParameterCount));
        for (Constructor<?> c : ctors) {
            System.out.println("PortScanner(" + Arrays.stream(c.getParameterTypes()).map(Class::getName)
                    .collect(Collectors.joining(", ")) + ")");
        }

        // Plugin loading: class names come from configuration
        for (String name : List.of("ConstructorsExample$PortScanner", "ConstructorsExample$TlsScanner", "java.lang.String")) {
            try {
                Class<? extends Scanner> type = Class.forName(name).asSubclass(Scanner.class);
                Scanner scanner = type.getDeclaredConstructor().newInstance();
                System.out.println("loaded " + type.getSimpleName() + ": " + scanner.scan("10.0.0.5"));
            } catch (ClassCastException e) {
                System.out.println("rejected " + name + ": not a Scanner");
            }
        }

        // Choosing a constructor by parameter types; int.class, not Integer.class
        Constructor<PortScanner> withMax = PortScanner.class.getConstructor(int.class);
        System.out.println(withMax.newInstance(443).scan("db"));
        try {
            withMax.newInstance(70_000);
        } catch (InvocationTargetException e) {
            System.out.println("constructor threw: " + e.getCause().getMessage());
        }
        try {
            PortScanner.class.getConstructor(String.class);
        } catch (NoSuchMethodException e) {
            System.out.println("no PortScanner(String) constructor: NoSuchMethodException");
        }

        // Records: the canonical constructor matches the record components
        Class<?>[] componentTypes = Arrays.stream(Finding.class.getRecordComponents())
                .map(RecordComponent::getType).toArray(Class<?>[]::new);
        Finding f = Finding.class.getDeclaredConstructor(componentTypes).newInstance("10.0.0.7", 9);
        System.out.println("record via canonical constructor: " + f);

        // A private constructor does not stop reflection
        Constructor<Vault> hidden = Vault.class.getDeclaredConstructor();
        hidden.setAccessible(true);
        Vault second = hidden.newInstance();
        System.out.println("second Vault created, same as INSTANCE: " + (second == Vault.INSTANCE));

        // ...but enums are protected by the JVM
        Constructor<?> enumCtor = Mode.class.getDeclaredConstructors()[0];
        enumCtor.setAccessible(true);
        try {
            enumCtor.newInstance("PARANOID", 2);
        } catch (IllegalArgumentException e) {
            System.out.println("new enum constant: IllegalArgumentException (" + e.getMessage() + ")");
        }
    }
}

/* Output:
PortScanner()
PortScanner(int)
loaded PortScanner: port scan of 10.0.0.5 up to 1024
loaded TlsScanner: TLS check of 10.0.0.5
rejected java.lang.String: not a Scanner
port scan of db up to 443
constructor threw: bad port 70000
no PortScanner(String) constructor: NoSuchMethodException
record via canonical constructor: Finding[host=10.0.0.7, severity=9]
second Vault created, same as INSTANCE: false
new enum constant: IllegalArgumentException (Cannot reflectively create enum objects)
*/
