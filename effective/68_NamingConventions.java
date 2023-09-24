// Effective Java, Item 68: Adhere to generally accepted naming conventions.
//
// Names are read far more often than written, and Java readers rely on a
// shared set of conventions. Typographical ones: packages are lowercase
// dotted words, types are UpperCamelCase, methods and fields lowerCamelCase,
// constants (static final, deeply immutable) UPPER_SNAKE_CASE, type
// parameters a single capital (T, E, K, V, X, R). Grammatical ones: classes
// are nouns, methods are verbs; boolean queries start with is/has, accessors
// are nouns or getX, converters are toType (new object), asType (view) or
// typeValue (primitive), and static factories are from, of, valueOf,
// instance/getInstance, create/newInstance, getType/newType. Breaking them
// makes an API surprising; this program checks a few names with a tiny
// linter.
import java.util.List;
import java.util.regex.Pattern;

public class NamingConventions {
    enum Kind {
        PACKAGE("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*"),
        TYPE("[A-Z][A-Za-z0-9]*"),
        METHOD_OR_FIELD("[a-z][A-Za-z0-9]*"),
        CONSTANT("[A-Z][A-Z0-9]*(_[A-Z0-9]+)*"),
        TYPE_PARAMETER("[A-Z]");

        final Pattern pattern;

        Kind(String regex) {
            this.pattern = Pattern.compile(regex);
        }
    }

    record Name(Kind kind, String text) {}

    public static void main(String[] args) {
        List<Name> names = List.of(
                new Name(Kind.PACKAGE, "com.example.billing"),
                new Name(Kind.PACKAGE, "com.Example.Billing"),
                new Name(Kind.TYPE, "HttpUrlConnection"),
                new Name(Kind.TYPE, "http_url_connection"),
                new Name(Kind.METHOD_OR_FIELD, "removeIf"),
                new Name(Kind.METHOD_OR_FIELD, "Remove_If"),
                new Name(Kind.CONSTANT, "MAX_RETRY_COUNT"),
                new Name(Kind.CONSTANT, "maxRetryCount"),
                new Name(Kind.TYPE_PARAMETER, "T"),
                new Name(Kind.TYPE_PARAMETER, "Element"));
        for (Name n : names) {
            boolean ok = n.kind().pattern.matcher(n.text()).matches();
            System.out.printf("%-16s %-20s %s%n", n.kind(), n.text(), ok ? "ok" : "violates convention");
        }

        // Grammatical conventions, as followed by the JDK itself
        Integer boxed = Integer.valueOf(42);                  // static factory: valueOf
        long asLong = boxed.longValue();                      // primitive converter: typeValue
        String text = boxed.toString();                       // new object: toType
        List<Integer> view = java.util.Arrays.asList(1, 2, 3); // view: asType
        boolean empty = text.isEmpty();                       // boolean query: isX
        System.out.println("valueOf=" + boxed + " longValue=" + asLong + " toString=" + text
                + " asList=" + view + " isEmpty=" + empty);
    }
}

/* Output:
PACKAGE          com.example.billing  ok
PACKAGE          com.Example.Billing  violates convention
TYPE             HttpUrlConnection    ok
TYPE             http_url_connection  violates convention
METHOD_OR_FIELD  removeIf             ok
METHOD_OR_FIELD  Remove_If            violates convention
CONSTANT         MAX_RETRY_COUNT      ok
CONSTANT         maxRetryCount        violates convention
TYPE_PARAMETER   T                    ok
TYPE_PARAMETER   Element              violates convention
valueOf=42 longValue=42 toString=42 asList=[1, 2, 3] isEmpty=false
*/
