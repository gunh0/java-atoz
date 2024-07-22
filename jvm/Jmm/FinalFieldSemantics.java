// Final fields: the JMM's initialization-safety guarantee, and its limits
//
// JLS 17.5: when a constructor finishes, the values of the object's final
// fields (and everything reachable through them at that point) are frozen;
// any thread that obtains a reference to the object, even through a data
// race, sees those values. Non-final fields get no such promise: a racy
// reader may see the reference before the constructor's writes (default 0 or
// null). That is why immutable classes (String, records, value objects) are
// thread-safe without locks, and why a broken double-checked-locking
// singleton can expose a half-built object. Two conditions: do not let
// `this` escape from the constructor (the guarantee only covers readers that
// get the reference after construction), and do not mutate finals later
// through reflection, which is allowed only for ordinary instance fields,
// is not visible where javac inlined a constant, and is refused for static
// finals, records and hidden classes. The race itself is not reproducible on
// demand, so this program shows the rules that can be checked.
import java.lang.reflect.Field;

public class FinalFieldSemantics {
    static final class Point {               // immutable: safe to share through a race
        final int x, y;
        Point(int x, int y) { this.x = x; this.y = y; }
    }

    static final class Holder {
        final int computed;                  // set in the constructor: not a constant
        final int constant = 42;             // compile-time constant: inlined by javac
        Holder(int v) { computed = v; }
        int readConstant() { return constant; }  // compiled as "bipush 42", no field read
    }

    record Version(int major) { }

    static final int LIMIT = 10;
    static int idSeenByListener = -1;

    static final class Leaky {
        final int id;
        Leaky(int id) {
            onCreated(this);                 // `this` escapes BEFORE id is assigned
            this.id = id;
        }
    }

    static void onCreated(Leaky l) {         // e.g. a listener registry or a callback
        idSeenByListener = l.id;
    }

    public static void main(String[] args) throws Exception {
        // Reflection CAN change an ordinary final instance field...
        Holder h = new Holder(1);
        Field computed = Holder.class.getDeclaredField("computed");
        computed.setAccessible(true);
        computed.setInt(h, 2);
        System.out.println("reflectively set final 'computed': now " + h.computed);

        // ...but readers of an inlined constant never see the change
        Field constant = Holder.class.getDeclaredField("constant");
        constant.setAccessible(true);
        constant.setInt(h, 99);
        System.out.println("'constant' via reflection: " + constant.getInt(h) + ", via code: " + h.readConstant());

        // Static finals and record components refuse
        try {
            Field limit = FinalFieldSemantics.class.getDeclaredField("LIMIT");
            limit.setAccessible(true);
            limit.setInt(null, 11);
        } catch (IllegalAccessException e) {
            System.out.println("static final LIMIT: " + e.getClass().getSimpleName());
        }
        try {
            Field major = Version.class.getDeclaredField("major");
            major.setAccessible(true);
            major.setInt(new Version(1), 2);
        } catch (IllegalAccessException e) {
            System.out.println("record component: " + e.getClass().getSimpleName());
        }

        // `this` escaping from a constructor: whoever receives it early can see
        // a final field before it is assigned, even in the same thread
        Leaky l = new Leaky(7);
        System.out.println("final id: listener saw " + idSeenByListener + ", after construction " + l.id);

        // The safe pattern: final fields + publish after construction
        Point p = new Point(3, 4);
        System.out.println("Point shared after construction: x=" + p.x + ", y=" + p.y);
    }
}

/* Output:
reflectively set final 'computed': now 2
'constant' via reflection: 99, via code: 42
static final LIMIT: IllegalAccessException
record component: IllegalAccessException
final id: listener saw 0, after construction 7
Point shared after construction: x=3, y=4
*/
