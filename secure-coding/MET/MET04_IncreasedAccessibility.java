// MET04-J. Do not increase the accessibility of overridden or hidden methods
// https://wiki.sei.cmu.edu/confluence/display/java/MET04-J.+Do+not+increase+the+accessibility+of+overridden+or+hidden+methods
//
// Java lets an overriding method be MORE accessible than the method it
// overrides: protected can become public. A framework that keeps a
// dangerous operation protected (meant to be called only by the framework
// itself, after its own checks) loses that protection the moment one
// subclass overrides it as public: now any code holding a reference to the
// subclass can call it directly and skip the checks. Keep overrides at the
// same access level, and make framework methods that must not be exposed
// final (with a separate protected hook for subclasses to customize).
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class MET04_IncreasedAccessibility {
    public static void main(String[] args) throws Exception {
        Exporter good = new CsvExporter();
        Exporter bad = new PublicCsvExporter();

        // The intended path: export() authorizes, then calls write()
        System.out.println("export as guest: " + good.export("guest"));

        // Noncompliant: write() became public, so callers can skip export().
        // (Everything in this single file shares one package; from another
        // package only the public override would be callable.)
        PublicCsvExporter direct = (PublicCsvExporter) bad;
        direct.write();
        System.out.println("noncompliant: write() called directly, rows leaked: " + direct.leaked);

        for (Class<?> c : List.of(Exporter.class, CsvExporter.class, PublicCsvExporter.class)) {
            Method m = c.getDeclaredMethod("write");
            System.out.println("  " + c.getSimpleName() + ".write() is " + Modifier.toString(m.getModifiers()));
        }
        System.out.println("compliant: Exporter.export() is final: "
                + Modifier.isFinal(Exporter.class.getDeclaredMethod("export", String.class).getModifiers()));
    }
}

abstract class Exporter {
    protected final List<String> rows = new ArrayList<>(List.of("alice,salary,9000", "bob,salary,8000"));

    // Template method: the only public entry point, and final
    public final String export(String role) {
        if (!role.equals("admin")) {
            return "denied";
        }
        write();
        return "exported";
    }

    // Subclasses implement the format; callers must go through export()
    protected abstract void write();
}

// Compliant: keeps the inherited access level
class CsvExporter extends Exporter {
    @Override
    protected void write() {
        // write rows as CSV
    }
}

// Noncompliant: widens protected to public
class PublicCsvExporter extends Exporter {
    int leaked;

    @Override
    public void write() {
        leaked = rows.size();
    }
}

/* Output:
export as guest: denied
noncompliant: write() called directly, rows leaked: 2
  Exporter.write() is protected abstract
  CsvExporter.write() is protected
  PublicCsvExporter.write() is public
compliant: Exporter.export() is final: true
*/
