// Effective Java, Item 19: Design and document for inheritance or else
// prohibit it.
//
// A class meant to be extended must document its self-use: which overridable
// methods it calls and when (the @implSpec tag exists for this). It must
// also never call overridable methods from a constructor (or from clone or
// readObject): the superclass constructor runs before the subclass's fields
// are initialized, so the override sees a half-built object. Designing for
// inheritance is a long-term commitment, so the default for ordinary
// concrete classes should be to forbid it: make the class final, or give it
// private constructors and static factories.
import java.util.ArrayList;
import java.util.List;

public class DesignForInheritance {
    public static void main(String[] args) {
        // The override runs before Sub's constructor body: label is still null
        Sub sub = new Sub();
        sub.overrideMe();

        // Constructor calling an overridable method leads to an NPE in practice
        try {
            new AuditedList();
        } catch (NullPointerException e) {
            System.out.println("half-built subclass: NullPointerException");
        }

        // Safe base class: constructor only calls private (non-overridable) code
        SafeAudited safe = new SafeAuditedSub();
        System.out.println("safe base: " + safe.describe());

        // Prohibiting inheritance: final class, or private constructor + factory
        System.out.println("final class: " + Temperature.ofCelsius(21.5));
    }
}

class Super {
    // Broken: constructor invokes an overridable method
    Super() {
        overrideMe();
    }

    void overrideMe() {
    }
}

final class Sub extends Super {
    private final String label; // set by the constructor

    Sub() {
        label = "initialized";
    }

    @Override
    void overrideMe() {
        System.out.println("overrideMe sees label = " + label);
    }
}

class AuditedBase {
    AuditedBase() {
        record("created"); // overridable call from the constructor
    }

    void record(String event) {
    }
}

class AuditedList extends AuditedBase {
    private final List<String> events = new ArrayList<>();

    @Override
    void record(String event) {
        events.add(event); // events is still null when called from super()
    }
}

class SafeAudited {
    private final List<String> events = new ArrayList<>();

    SafeAudited() {
        recordInternal("created");
    }

    /**
     * Records an event.
     *
     * @implSpec This implementation delegates to a private helper; it is not
     * called by any other method of this class, so overriding it changes
     * nothing else.
     */
    void record(String event) {
        recordInternal(event);
    }

    private void recordInternal(String event) {
        events.add(event);
    }

    String describe() {
        return getClass().getSimpleName() + " " + events;
    }
}

class SafeAuditedSub extends SafeAudited {
    private final String tag = "sub";

    @Override
    void record(String event) {
        super.record(tag + ":" + event);
    }
}

final class Temperature {
    private final double celsius;

    private Temperature(double celsius) {
        this.celsius = celsius;
    }

    static Temperature ofCelsius(double celsius) {
        return new Temperature(celsius);
    }

    @Override
    public String toString() {
        return celsius + " C";
    }
}

/* Output:
overrideMe sees label = null
overrideMe sees label = initialized
half-built subclass: NullPointerException
safe base: SafeAuditedSub [created]
final class: 21.5 C
*/
