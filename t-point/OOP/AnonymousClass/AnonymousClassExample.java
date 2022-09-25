// Two more kinds of nested class live inside a method body:
//  - a local class has a name but is visible only inside its block
//  - an anonymous class is declared and instantiated in one expression,
//    new Interface() { ... } or new SomeClass(args) { ... }, for one-off use
// Both can capture local variables only if those are final or effectively
// final (never reassigned).
// Inside an anonymous class `this` is the anonymous object itself; inside a
// lambda `this` is the enclosing instance. A lambda can only implement a
// functional interface, an anonymous class can also extend a class or keep state.
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AnonymousClassExample {
    private final String name = "outer";

    public static void main(String[] args) {
        new AnonymousClassExample().run();
    }

    void run() {
        List<String> files = new ArrayList<>(List.of("report.pdf", "a.txt", "notes.md", "archive.tar.gz"));

        // Anonymous class implementing Comparator
        files.sort(new Comparator<String>() {
            @Override
            public int compare(String x, String y) {
                return Integer.compare(x.length(), y.length());
            }
        });
        System.out.println("by length: " + files);

        // Anonymous subclass with its own state: something a lambda cannot do
        String prefix = "LOG";                       // effectively final, so it can be captured
        Counter counter = new Counter() {
            private int calls;

            @Override
            void log(String message) {
                calls++;
                System.out.println(prefix + " #" + calls + " " + message);
            }
        };
        counter.log("started");
        counter.log("finished");
        // prefix = "X";   // would make prefix not effectively final: capture fails to compile

        // Local class: named, reusable within this method only
        class Extension {
            String of(String file) {
                int dot = file.indexOf('.');
                return dot < 0 ? "" : file.substring(dot + 1);
            }
        }
        Extension ext = new Extension();
        for (String f : files) {
            System.out.println(f + " -> " + ext.of(f));
        }

        // `this` differs between an anonymous class and a lambda
        Runnable anon = new Runnable() {
            @Override
            public void run() {
                System.out.println("anonymous: this is an anonymous class? " + getClass().isAnonymousClass()
                        + ", outer name via AnonymousClassExample.this.name = " + AnonymousClassExample.this.name);
            }
        };
        Runnable lambda = () -> System.out.println("lambda: this.name = " + this.name);
        anon.run();
        lambda.run();
    }
}

abstract class Counter {
    abstract void log(String message);
}

/* Output:
by length: [a.txt, notes.md, report.pdf, archive.tar.gz]
LOG #1 started
LOG #2 finished
a.txt -> txt
notes.md -> md
report.pdf -> pdf
archive.tar.gz -> tar.gz
anonymous: this is an anonymous class? true, outer name via AnonymousClassExample.this.name = outer
lambda: this.name = outer
*/
