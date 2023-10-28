// Every type the JVM loads is described by one java.lang.Class object, the
// entry point to reflection. Three ways to get it:
//   Firewall.class               known at compile time (also int.class, String[].class)
//   obj.getClass()               the runtime class of an object
//   Class.forName("pkg.Name")    by binary name, loading it if needed
// From a Class you can ask for names, modifiers, the superclass and
// interfaces, what kind of type it is, and test or cast objects with it.
// There is exactly one Class object per type per class loader, so == works.
import java.io.Serializable;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClassObjectExample {
    interface Rule {}

    static abstract class Device implements Serializable {}

    static final class Firewall extends Device implements Rule, Comparable<Firewall> {
        public int compareTo(Firewall o) { return 0; }
    }

    record Port(int number) {}

    enum Protocol { TCP, UDP }

    public static void main(String[] args) throws ClassNotFoundException {
        Class<Firewall> literal = Firewall.class;
        Class<?> runtime = new Firewall().getClass();
        Class<?> byName = Class.forName("ClassObjectExample$Firewall"); // binary name of a nested class
        System.out.println("same Class object: " + (literal == runtime && runtime == byName));

        // Names
        System.out.println("getName:          " + literal.getName());
        System.out.println("getSimpleName:    " + literal.getSimpleName());
        System.out.println("getCanonicalName: " + literal.getCanonicalName());
        System.out.println("ArrayList:        " + ArrayList.class.getName() + " in package " + ArrayList.class.getPackageName());
        System.out.println("int[] name:       " + int[].class.getName() + ", String[] name: " + String[].class.getName());

        // Modifiers, hierarchy
        System.out.println("modifiers:        " + Modifier.toString(literal.getModifiers()));
        System.out.println("superclass chain: " + superclasses(literal));
        System.out.println("interfaces:       " + Arrays.stream(literal.getInterfaces()).map(Class::getSimpleName).toList());
        System.out.println("generic ifaces:   " + Arrays.stream(literal.getGenericInterfaces()).map(t -> t.getTypeName()).toList());
        System.out.println("Device abstract:  " + Modifier.isAbstract(Device.class.getModifiers()));

        // Kinds of types
        for (Class<?> c : List.of(Rule.class, Port.class, Protocol.class, int.class, String[].class, Firewall.class)) {
            System.out.printf("%-10s interface=%-5b record=%-5b enum=%-5b primitive=%-5b array=%b%n",
                    c.getSimpleName(), c.isInterface(), c.isRecord(), c.isEnum(), c.isPrimitive(), c.isArray());
        }
        System.out.println("String[] component type: " + String[].class.getComponentType().getSimpleName());
        System.out.println("int.class == Integer.TYPE: " + (int.class == Integer.TYPE) + ", int.class == Integer.class: " + (int.class == Integer.class));

        // Type tests: the reflective instanceof and assignment checks
        Object device = new Firewall();
        System.out.println("Rule.class.isInstance(device): " + Rule.class.isInstance(device));
        System.out.println("Device assignable from Firewall: " + Device.class.isAssignableFrom(Firewall.class));
        System.out.println("Firewall assignable from Device: " + Firewall.class.isAssignableFrom(Device.class));
        Firewall fw = Firewall.class.cast(device); // checked cast
        System.out.println("cast ok: " + (fw != null));
        try {
            Port.class.cast(device);
        } catch (ClassCastException e) {
            System.out.println("Port.class.cast(firewall): ClassCastException");
        }

        try {
            Class.forName("com.example.Missing");
        } catch (ClassNotFoundException e) {
            System.out.println("Class.forName(unknown): ClassNotFoundException " + e.getMessage());
        }
    }

    static List<String> superclasses(Class<?> c) {
        List<String> chain = new ArrayList<>();
        for (Class<?> k = c; k != null; k = k.getSuperclass()) chain.add(k.getSimpleName());
        return chain;
    }
}

/* Output:
same Class object: true
getName:          ClassObjectExample$Firewall
getSimpleName:    Firewall
getCanonicalName: ClassObjectExample.Firewall
ArrayList:        java.util.ArrayList in package java.util
int[] name:       [I, String[] name: [Ljava.lang.String;
modifiers:        static final
superclass chain: [Firewall, Device, Object]
interfaces:       [Rule, Comparable]
generic ifaces:   [ClassObjectExample$Rule, java.lang.Comparable<ClassObjectExample$Firewall>]
Device abstract:  true
Rule       interface=true  record=false enum=false primitive=false array=false
Port       interface=false record=true  enum=false primitive=false array=false
Protocol   interface=false record=false enum=true  primitive=false array=false
int        interface=false record=false enum=false primitive=true  array=false
String[]   interface=false record=false enum=false primitive=false array=true
Firewall   interface=false record=false enum=false primitive=false array=false
String[] component type: String
int.class == Integer.TYPE: true, int.class == Integer.class: false
Rule.class.isInstance(device): true
Device assignable from Firewall: true
Firewall assignable from Device: false
cast ok: true
Port.class.cast(firewall): ClassCastException
Class.forName(unknown): ClassNotFoundException com.example.Missing
*/
