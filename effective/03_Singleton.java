// Effective Java, Item 3: Enforce the singleton property with a private
// constructor or an enum type.
//
// A private constructor plus a public static final field (or a static factory)
// is the classic singleton, but it has two holes: privileged code can call the
// private constructor through reflection, and a Serializable singleton is
// duplicated by every deserialization unless it defines readResolve().
// A single-element enum closes both holes for free, which makes it the
// preferred way to implement a singleton (unless it must extend a class).
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;

public class SingletonDemo {
    public static void main(String[] args) throws Exception {
        // 1. Reflection attack on the field-based singleton
        Constructor<FieldElvis> ctor = FieldElvis.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        FieldElvis impostor = ctor.newInstance();
        System.out.println("field singleton, reflection gives a second instance: "
                + (impostor != FieldElvis.INSTANCE));

        // 2. Serialization without readResolve() breaks the guarantee
        System.out.println("factory singleton without readResolve, copy is same instance: "
                + (roundTrip(FactoryElvis.getInstance()) == FactoryElvis.getInstance()));
        System.out.println("factory singleton with readResolve, copy is same instance: "
                + (roundTrip(ResolvingElvis.getInstance()) == ResolvingElvis.getInstance()));

        // 3. Enum singleton: reflection is refused, serialization keeps identity
        Constructor<?> enumCtor = EnumElvis.class.getDeclaredConstructors()[0];
        enumCtor.setAccessible(true);
        try {
            enumCtor.newInstance("IMPOSTOR", 1);
        } catch (IllegalArgumentException e) {
            System.out.println("enum singleton, reflection: " + e.getMessage());
        }
        System.out.println("enum singleton, copy is same instance: "
                + (roundTrip(EnumElvis.INSTANCE) == EnumElvis.INSTANCE));
        EnumElvis.INSTANCE.leaveTheBuilding();
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T object) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(object);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) in.readObject();
        }
    }
}

// Public final field: simple, and the API makes it clear that this is a singleton
class FieldElvis {
    public static final FieldElvis INSTANCE = new FieldElvis();

    private FieldElvis() {
        // Defense against reflection would go here: throw if INSTANCE already exists
    }
}

// Static factory: lets you change your mind later (per-thread instance, generic
// factory, Supplier<FactoryElvis> via FactoryElvis::getInstance)
class FactoryElvis implements Serializable {
    private static final FactoryElvis INSTANCE = new FactoryElvis();

    private FactoryElvis() {
    }

    static FactoryElvis getInstance() {
        return INSTANCE;
    }
}

// Same as above, but deserialization is redirected to the one true instance
class ResolvingElvis implements Serializable {
    private static final ResolvingElvis INSTANCE = new ResolvingElvis();

    private ResolvingElvis() {
    }

    static ResolvingElvis getInstance() {
        return INSTANCE;
    }

    // Called after deserialization; the freshly created copy is discarded.
    // (All instance fields should also be transient, or an attacker can still
    // capture the copy before readResolve runs.)
    private Object readResolve() {
        return INSTANCE;
    }
}

// Recommended: a single-element enum
enum EnumElvis {
    INSTANCE;

    void leaveTheBuilding() {
        System.out.println("Whoa baby, I'm outta here!");
    }
}

/* Output:
field singleton, reflection gives a second instance: true
factory singleton without readResolve, copy is same instance: false
factory singleton with readResolve, copy is same instance: true
enum singleton, reflection: Cannot reflectively create enum objects
enum singleton, copy is same instance: true
Whoa baby, I'm outta here!
*/
