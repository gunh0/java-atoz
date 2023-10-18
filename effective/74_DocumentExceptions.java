// Effective Java, Item 74: Document all exceptions thrown by each method.
//
// Declare each checked exception individually in the throws clause and
// document with Javadoc @throws exactly the conditions under which every
// exception - checked or unchecked - is thrown. Never declare `throws
// Exception` or `throws Throwable`: it hides what can actually go wrong and
// forces callers to catch everything. Unchecked exceptions go in @throws but
// not in the throws clause, so readers can tell the two kinds apart. If many
// methods of a class throw the same exception for the same reason, it may be
// documented once in the class comment.
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

public class DocumentExceptions {
    /** Loads something. */
    static String loadVague(String name) throws Exception { // tells callers nothing
        throw new FileNotFoundException(name);
    }

    /**
     * Loads a resource by name.
     *
     * @param name the resource name, must not be blank
     * @return the resource content
     * @throws FileNotFoundException if no resource has that name
     * @throws TimeoutException if the backing store does not answer in time
     * @throws IllegalArgumentException if {@code name} is blank (unchecked,
     *         so documented here but not in the throws clause)
     */
    static String load(String name) throws FileNotFoundException, TimeoutException {
        if (name.isBlank()) {
            throw new IllegalArgumentException("blank name");
        }
        if (name.equals("slow")) {
            throw new TimeoutException("store did not answer");
        }
        throw new FileNotFoundException(name);
    }

    public static void main(String[] args) throws Exception {
        for (String m : new String[] {"loadVague", "load"}) {
            Method method = DocumentExceptions.class.getDeclaredMethod(m, String.class);
            System.out.println(m + " declares " + Arrays.stream(method.getExceptionTypes())
                    .map(Class::getSimpleName).toList());
        }

        // Vague: one catch for everything, including bugs
        try {
            loadVague("config");
        } catch (Exception e) {
            System.out.println("vague caller can only say: something failed (" + e.getClass().getSimpleName() + ")");
        }

        // Precise: each documented condition gets its own handling
        for (String name : new String[] {"config", "slow", " "}) {
            try {
                load(name);
            } catch (FileNotFoundException e) {
                System.out.println("'" + name + "': use defaults instead");
            } catch (TimeoutException e) {
                System.out.println("'" + name + "': retry later");
            } catch (IllegalArgumentException e) {
                System.out.println("'" + name + "': caller bug -> " + e.getMessage());
            }
        }

        // An IOException subclass is still documented by its own name
        System.out.println("FileNotFoundException is an IOException: "
                + IOException.class.isAssignableFrom(FileNotFoundException.class));
    }
}

/* Output:
loadVague declares [Exception]
load declares [FileNotFoundException, TimeoutException]
vague caller can only say: something failed (FileNotFoundException)
'config': use defaults instead
'slow': retry later
' ': caller bug -> blank name
FileNotFoundException is an IOException: true
*/
