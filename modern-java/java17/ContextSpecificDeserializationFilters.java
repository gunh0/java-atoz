// JEP 415 — Context-Specific Deserialization Filters (Java 17)
//
// Deserializing untrusted bytes lets the sender choose which classes get
// instantiated and whose readObject() runs: the root of countless "gadget
// chain" remote-code-execution bugs. Java 9 (JEP 290) added filters, but
// only one static JVM-wide filter (jdk.serialFilter) plus per-stream
// filters that every call site, including those deep inside libraries,
// had to remember to set. Java 17 adds a JVM-wide filter FACTORY: it is
// consulted for every ObjectInputStream and can choose a filter based on
// the current context (thread, caller, task), and new helpers
// allowFilter/rejectFilter/merge/rejectUndecidedClass to build allow-lists.
// Here a factory applies whatever filter the current thread's context
// installed, so library code reading streams is covered automatically.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.concurrent.Callable;

public class ContextSpecificDeserializationFilters {
    // Something the application expects to receive
    static class Note implements Serializable {
        private static final long serialVersionUID = 1L;
        final String text;

        Note(String text) {
            this.text = text;
        }
    }

    // Something it does NOT expect: its readObject has a side effect
    static class Command implements Serializable {
        private static final long serialVersionUID = 1L;

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            System.out.println("  !! Command.readObject ran during deserialization");
        }
    }

    static final ThreadLocal<ObjectInputFilter> CONTEXT_FILTER = new ThreadLocal<>();

    public static void main(String[] args) throws Exception {
        // Install once, at startup: every ObjectInputStream now asks the factory for its filter
        ObjectInputFilter.Config.setSerialFilterFactory((current, requested) -> {
            ObjectInputFilter context = CONTEXT_FILTER.get();
            if (context == null) {
                return requested;               // no context: behave like before
            }
            if (requested == null) {
                return context;
            }
            return ObjectInputFilter.merge(context, requested); // both must allow
        });

        byte[] note = serialize(new Note("meet at 10"));
        byte[] command = serialize(new Command());

        // Before: a library reads a stream without setting any filter
        System.out.println("no filter:");
        System.out.println("  note    -> " + describe(LibraryCode.read(note)));
        System.out.println("  command -> " + describe(LibraryCode.read(command)));

        // After: the request handler runs with an allow-list; the library code is unchanged
        ObjectInputFilter allowList = ObjectInputFilter.rejectUndecidedClass(
                ObjectInputFilter.Config.createFilter(Note.class.getName() + ";java.lang.String;maxdepth=5"));
        System.out.println("context allow-list:");
        System.out.println("  note    -> " + inContext(allowList, () -> describe(LibraryCode.read(note))));
        System.out.println("  command -> " + inContext(allowList, () -> describe(LibraryCode.read(command))));

        // Java 17 helper: a predicate-based filter
        ObjectInputFilter noCommands = ObjectInputFilter.rejectFilter(Command.class::equals, ObjectInputFilter.Status.UNDECIDED);
        System.out.println("rejectFilter(Command):");
        System.out.println("  command -> " + inContext(noCommands, () -> describe(LibraryCode.read(command))));

        // The factory can be set only once per JVM
        try {
            ObjectInputFilter.Config.setSerialFilterFactory((c, r) -> r);
        } catch (IllegalStateException e) {
            System.out.println("second setSerialFilterFactory: IllegalStateException");
        }
    }

    // Stands for third-party code that knows nothing about filters
    static class LibraryCode {
        static Object read(byte[] bytes) throws IOException, ClassNotFoundException {
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                return in.readObject();
            }
        }
    }

    interface IoCall<T> {
        T call() throws Exception;
    }

    static String inContext(ObjectInputFilter filter, IoCall<String> work) throws Exception {
        CONTEXT_FILTER.set(filter);
        try {
            return work.call();
        } catch (InvalidClassException e) {
            return "rejected (" + e.getMessage() + ")";
        } finally {
            CONTEXT_FILTER.remove();
        }
    }

    static String describe(Object o) {
        return o instanceof Note ? "Note \"" + ((Note) o).text + "\"" : o.getClass().getSimpleName();
    }

    static byte[] serialize(Object o) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(o);
        }
        return bytes.toByteArray();
    }
}

/* Output:
no filter:
  note    -> Note "meet at 10"
  !! Command.readObject ran during deserialization
  command -> Command
context allow-list:
  note    -> Note "meet at 10"
  command -> rejected (filter status: REJECTED)
rejectFilter(Command):
  command -> rejected (filter status: REJECTED)
second setSerialFilterFactory: IllegalStateException
*/
