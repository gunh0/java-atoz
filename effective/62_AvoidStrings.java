// Effective Java, Item 62: Avoid strings where other types are more appropriate.
//
// Strings are so convenient that they get used for everything. They are a
// poor substitute for other value types (parse "42" into an int, "true"
// into a boolean as soon as it is read), for enums, and for aggregate
// types: a key built as className + "#" + id is ambiguous when a part can
// contain the delimiter, must be parsed to get the parts back, and has no
// place for equals or behaviour. Strings are also poor capabilities: if
// unrelated clients share a namespace of string keys, two of them can pick
// the same key by accident (or on purpose) and see each other's data. Use a
// record for aggregates and an unforgeable key object for capabilities.
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class AvoidStrings {
    public static void main(String[] args) {
        // Aggregate as string: two different (type, id) pairs collide
        Map<String, String> byStringKey = new HashMap<>();
        byStringKey.put("user#admin" + "#" + "42", "first");
        byStringKey.put("user" + "#" + "admin#42", "second");
        System.out.println("string keys stored: " + byStringKey.size() + " -> " + byStringKey);

        // Aggregate as a record: the parts stay separate
        Map<ResourceKey, String> byRecordKey = new HashMap<>();
        byRecordKey.put(new ResourceKey("user#admin", "42"), "first");
        byRecordKey.put(new ResourceKey("user", "admin#42"), "second");
        System.out.println("record keys stored: " + byRecordKey.size());

        // Capability as string: two libraries both choose the key "session"
        StringKeyedLocals.set("session", "library A data");
        StringKeyedLocals.set("session", "library B data");
        System.out.println("library A reads: " + StringKeyedLocals.get("session"));

        // Capability as an object: each key is unique and unforgeable
        Key a = new Key();
        Key b = new Key();
        KeyedLocals.set(a, "library A data");
        KeyedLocals.set(b, "library B data");
        System.out.println("library A reads: " + KeyedLocals.get(a));

        // Values as strings: compare "true" to the real type
        String flagText = "TRUE";
        System.out.println("\"TRUE\".equals(\"true\"): " + flagText.equals("true")
                + ", Boolean.parseBoolean: " + Boolean.parseBoolean(flagText));
        String amountText = "0042";
        System.out.println("as strings \"0042\" vs \"42\" equal? " + amountText.equals("42")
                + ", as ints: " + (Integer.parseInt(amountText) == 42));
    }
}

record ResourceKey(String type, String id) {
    ResourceKey {
        Objects.requireNonNull(type);
        Objects.requireNonNull(id);
    }
}

// Shared namespace keyed by client-chosen strings
final class StringKeyedLocals {
    private static final Map<String, Object> VALUES = new HashMap<>();

    static void set(String key, Object value) {
        VALUES.put(key, value);
    }

    static Object get(String key) {
        return VALUES.get(key);
    }
}

// Keys are objects: equality is identity, so no one can forge or collide with yours
final class Key {
}

final class KeyedLocals {
    private static final Map<Key, Object> VALUES = new HashMap<>();

    static void set(Key key, Object value) {
        VALUES.put(key, value);
    }

    static Object get(Key key) {
        return VALUES.get(key);
    }
}

/* Output:
string keys stored: 1 -> {user#admin#42=second}
record keys stored: 2
library A reads: library B data
library A reads: library A data
"TRUE".equals("true"): false, Boolean.parseBoolean: true
as strings "0042" vs "42" equal? false, as ints: true
*/
