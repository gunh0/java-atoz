// OBJ03-J. Prevent heap pollution
// https://wiki.sei.cmu.edu/confluence/display/java/OBJ03-J.+Prevent+heap+pollution
//
// Generics are checked at compile time and erased at run time, so a
// List<String> is just a List to the JVM. Raw types, unchecked casts and
// generic varargs let an object of the wrong type into a parameterized
// collection without any error ("heap pollution"). The failure, a
// ClassCastException, appears much later, in code that did nothing wrong,
// and the bad value may already have been used. Do not mix raw and
// generic types, do not ignore unchecked warnings, and when a collection
// is handed to code you do not trust, wrap it with
// Collections.checkedList / checkedMap so the bad insert fails at once.
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OBJ03_HeapPollution {
    public static void main(String[] args) {
        // Noncompliant: legacy code with a raw type inserts an Integer
        List<String> allowedHosts = new ArrayList<>(List.of("api.example.com"));
        LegacyPlugin.addDefaults(allowedHosts);
        System.out.println("noncompliant: list now has " + allowedHosts.size() + " entries, no error yet");
        try {
            for (String host : allowedHosts) {
                System.out.println("  checking " + host.toLowerCase());
            }
        } catch (ClassCastException e) {
            System.out.println("  ClassCastException, far from the code that caused it");
        }

        // Compliant: a checked view rejects the bad element at the insert
        List<String> checked = Collections.checkedList(new ArrayList<>(List.of("api.example.com")), String.class);
        try {
            LegacyPlugin.addDefaults(checked);
        } catch (ClassCastException e) {
            System.out.println("compliant: ClassCastException inside addDefaults, list still " + checked);
        }

        // Generic varargs: the List<String>[] parameter is a plain List[] at run time
        Object first = Varargs.firstAfterPollution(List.of("alice"), List.of("bob"));
        System.out.println("noncompliant varargs: element of a List<String> has class " + first.getClass().getSimpleName());
    }
}

class LegacyPlugin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void addDefaults(List hosts) {
        hosts.add(8080); // a port where a host name was expected
    }
}

class Varargs {
    // javac warns "possible heap pollution from parameterized vararg type"; it is right
    @SuppressWarnings("unchecked")
    static Object firstAfterPollution(List<String>... lists) {
        Object[] array = lists;      // legal: arrays are covariant
        array[0] = List.of(42);      // no ArrayStoreException: the array's type is erased
        return lists[0].get(0);      // typed as String, actually an Integer
    }
}

/* Output:
OBJ03_HeapPollution.java:40: warning: [unchecked] unchecked generic array creation for varargs parameter of type List<String>[]
        Object first = Varargs.firstAfterPollution(List.of("alice"), List.of("bob"));
                                                  ^
1 warning
noncompliant: list now has 2 entries, no error yet
  checking api.example.com
  ClassCastException, far from the code that caused it
compliant: ClassCastException inside addDefaults, list still [api.example.com]
noncompliant varargs: element of a List<String> has class Integer
*/
