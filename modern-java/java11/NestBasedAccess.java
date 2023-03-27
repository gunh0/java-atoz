// JEP 181 — Nest-Based Access Control (Java 11)
//
// The language has always let a nested class use the private members of
// its enclosing class (and vice versa), but the JVM saw them as unrelated
// classes. javac bridged the gap by generating package-private synthetic
// accessors (access$000 ...), which widened access to the whole package,
// bloated class files, and left reflection inconsistent with the language:
// Inner could call outer.secret() directly but got IllegalAccessException
// doing the same via Method.invoke. Java 11 records "nests" in the class
// file (NestHost / NestMembers attributes): nestmates may access each
// other's privates natively, no bridges are generated, and reflection
// follows the same rule. Class.getNestHost/getNestMembers/isNestmateOf
// expose the relationship.
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

public class NestBasedAccess {
    public static void main(String[] args) throws Exception {
        // The nest: the top-level class and everything nested in it
        System.out.println("nest host of Vault.Teller: " + Vault.Teller.class.getNestHost().getSimpleName());
        System.out.println("nest members of Vault: " + Arrays.stream(Vault.class.getNestMembers())
                .map(Class::getSimpleName).sorted().collect(Collectors.toList()));
        System.out.println("Teller nestmate of Vault:    " + Vault.Teller.class.isNestmateOf(Vault.class));
        System.out.println("Outsider nestmate of Vault:  " + Outsider.class.isNestmateOf(Vault.class));

        // Direct access from a nestmate needs no generated bridge any more
        Vault vault = new Vault();
        Vault.Teller teller = new Vault.Teller();
        System.out.println("direct access:     " + teller.peek(vault));
        String bridges = Arrays.stream(Vault.class.getDeclaredMethods())
                .filter(Method::isSynthetic).map(Method::getName).collect(Collectors.joining(","));
        System.out.println("synthetic access$ methods in Vault: [" + bridges + "]");

        // Reflection now agrees with the language: a nestmate may invoke a private method
        System.out.println("reflective access: " + teller.peekReflectively(vault));

        // ... while a class outside the nest is still refused
        try {
            new Outsider().peekReflectively(vault);
        } catch (IllegalAccessException e) {
            System.out.println("outsider: IllegalAccessException");
        }
    }
}

class Vault {
    private String combination() {
        return "12-34-56";
    }

    static class Teller {
        String peek(Vault v) {
            return v.combination(); // pre-Java 11: compiled as a call to Vault.access$000(v)
        }

        String peekReflectively(Vault v) throws Exception {
            Method m = Vault.class.getDeclaredMethod("combination");
            return (String) m.invoke(v); // pre-Java 11: IllegalAccessException without setAccessible
        }
    }
}

class Outsider {
    String peekReflectively(Vault v) throws Exception {
        Method m = Vault.class.getDeclaredMethod("combination");
        return (String) m.invoke(v);
    }
}

/* Output:
nest host of Vault.Teller: Vault
nest members of Vault: [Teller, Vault]
Teller nestmate of Vault:    true
Outsider nestmate of Vault:  false
direct access:     12-34-56
synthetic access$ methods in Vault: []
reflective access: 12-34-56
outsider: IllegalAccessException
*/
