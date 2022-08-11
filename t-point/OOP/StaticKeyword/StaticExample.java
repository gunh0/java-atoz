// `static` members belong to the CLASS, not to any one object.
//  - a static field has a single copy shared by every instance
//  - a static method runs without an instance, so it cannot use `this` or
//    touch instance fields directly
//  - a static initializer block runs once, when the class is initialised
//    (on first active use), before any instance is created
// Order for `new` on a fresh class: static fields/blocks (once, top to
// bottom), then instance fields/blocks, then the constructor body.
public class StaticExample {
    public static void main(String[] args) {
        System.out.println("main starts; Ticket not initialised yet");

        Ticket a = new Ticket("concert");
        Ticket b = new Ticket("museum");
        Ticket c = new Ticket("concert");

        System.out.println(a + ", " + b + ", " + c);
        // One shared counter, read through the class name
        System.out.println("tickets issued: " + Ticket.issued);
        System.out.println("price for 3 in EUR: " + Ticket.priceFor(3));

        // Static helper methods need no object at all
        System.out.println("max(4, 9) = " + MathUtil.max(4, 9));
    }
}

class Ticket {
    static int issued;                    // shared by all tickets
    static final int UNIT_PRICE_CENTS;    // constant set in the static block

    static {
        System.out.println("static block: Ticket class initialised");
        UNIT_PRICE_CENTS = 1250;
    }

    final int number;                     // one per object
    final String event;

    {
        System.out.println("instance block: before constructor body");
    }

    Ticket(String event) {
        issued++;                         // touching the shared counter
        this.number = issued;
        this.event = event;
        System.out.println("constructor: ticket #" + number);
    }

    static String priceFor(int count) {
        // `number` or `this` here would not compile: no instance exists
        int cents = count * UNIT_PRICE_CENTS;
        return cents / 100 + "." + String.format("%02d", cents % 100);
    }

    @Override
    public String toString() {
        return "#" + number + " " + event;
    }
}

final class MathUtil {
    private MathUtil() { }                // a bag of static methods: no instances

    static int max(int a, int b) {
        return a >= b ? a : b;
    }
}

/* Output:
main starts; Ticket not initialised yet
static block: Ticket class initialised
instance block: before constructor body
constructor: ticket #1
instance block: before constructor body
constructor: ticket #2
instance block: before constructor body
constructor: ticket #3
#1 concert, #2 museum, #3 concert
tickets issued: 3
price for 3 in EUR: 37.50
max(4, 9) = 9
*/
