// The order in which Java runs initialization code:
//  1. Class initialization, once, the first time the class is used:
//     superclass first, then static field initializers and static blocks of
//     the class in source order.
//  2. Each `new`: the constructor first calls super(...) (implicitly or
//     explicitly), all the way up; after super returns, the instance field
//     initializers and instance initializer blocks run in source order, and
//     only then the rest of the constructor body.
// Pitfall: a constructor that calls an overridable method lets the subclass
// run before its own fields are initialized, so it sees default values.
import java.util.ArrayList;
import java.util.List;

public class InitializationOrderExample {
    static final List<String> LOG = new ArrayList<>();

    static String log(String s) {
        LOG.add(s);
        return s;
    }

    static class Parent {
        static String staticField = log("Parent static field");

        static {
            log("Parent static block");
        }

        String instanceField = log("Parent instance field");

        {
            log("Parent instance block");
        }

        Parent() {
            log("Parent constructor");
            describe(); // overridable call from a constructor: dangerous
        }

        void describe() {
            log("Parent.describe()");
        }
    }

    static class Child extends Parent {
        static {
            log("Child static block");
        }

        static String staticField = log("Child static field");

        private final String name = "child";       // compile-time constant: inlined, already set
        private String role = log("Child instance field"); // assigned after super() returns

        {
            log("Child instance block");
        }

        Child() {
            super();
            log("Child constructor (role=" + (role == null ? "null" : "set") + ")");
        }

        @Override
        void describe() {
            // Runs during Parent's constructor: role has not been assigned yet
            log("Child.describe() sees role=" + role + ", name=" + name);
        }
    }

    public static void main(String[] args) {
        log("--- first new Child()");
        new Child();
        log("--- second new Child() (no static init this time)");
        new Child();
        LOG.forEach(System.out::println);
    }
}

/* Output:
--- first new Child()
Parent static field
Parent static block
Child static block
Child static field
Parent instance field
Parent instance block
Parent constructor
Child.describe() sees role=null, name=child
Child instance field
Child instance block
Child constructor (role=set)
--- second new Child() (no static init this time)
Parent instance field
Parent instance block
Parent constructor
Child.describe() sees role=null, name=child
Child instance field
Child instance block
Child constructor (role=set)
*/
