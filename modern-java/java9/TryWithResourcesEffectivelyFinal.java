// JEP 213 — Milling Project Coin: try-with-resources on effectively final
// variables (Java 9)
//
// In Java 7 and 8 the resource of a try-with-resources statement had to be
// declared in the statement itself. When the resource already existed (a
// parameter, a field, something built a few lines earlier) you had to
// introduce a dummy variable: try (Reader r2 = r1) { ... }. Since Java 9 a
// final or effectively final variable can be named directly, and it is
// closed exactly as if it had been declared there, in reverse order of
// appearance.
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class TryWithResourcesEffectivelyFinal {
    static final List<String> events = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        // Before Java 9: the existing reader needs a redundant alias
        BufferedReader reader = new BufferedReader(new StringReader("first line\nsecond line"));
        try (BufferedReader alias = reader) {
            System.out.println("before: " + alias.readLine());
        }

        // Java 9: just name the effectively final variable
        BufferedReader reader2 = new BufferedReader(new StringReader("third line\nfourth line"));
        try (reader2) {
            System.out.println("after:  " + reader2.readLine());
        }
        try {
            reader2.readLine();
        } catch (IOException e) {
            System.out.println("closed afterwards: " + e.getMessage());
        }

        // Several resources: closed in reverse order, even the ones created earlier
        Resource db = new Resource("db");
        Resource cache = new Resource("cache");
        try (db; cache; Resource log = new Resource("log")) {
            events.add("body");
        }
        System.out.println("events: " + events);

        // Works with method parameters too
        copyAndClose(new Resource("param"));
        System.out.println("events: " + events);

        // Not allowed: a variable that is reassigned is not effectively final
        // Resource r = new Resource("r"); r = new Resource("other");
        // try (r) { }   // error: must be final or effectively final
    }

    static void copyAndClose(Resource in) {
        try (in) {
            events.add("using " + in.name);
        }
    }

    static final class Resource implements AutoCloseable {
        final String name;

        Resource(String name) {
            this.name = name;
        }

        @Override
        public void close() {
            events.add("close " + name);
        }
    }
}

/* Output:
before: first line
after:  third line
closed afterwards: Stream closed
events: [body, close log, close cache, close db]
events: [body, close log, close cache, close db, using param, close param]
*/
