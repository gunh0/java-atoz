// `this` is a reference to the current object. Its common uses:
//  1. tell a field apart from a parameter with the same name (this.name = name)
//  2. call another constructor of the same class: this(...)
//  3. pass the current object to another method or object
//  4. return the current object so calls can be chained (fluent API)
// `this` does not exist in static methods, since there is no current object.
import java.util.ArrayList;
import java.util.List;

public class ThisKeywordExample {
    public static void main(String[] args) {
        Order order = new Order("A-100")
                .add("keyboard", 2)
                .add("mouse", 1)
                .add("monitor", 1);     // chaining works because add returns this
        System.out.println(order.summary());

        // Without `this.`, assigning a parameter to itself silently does nothing
        Shadowing s = new Shadowing("expected");
        System.out.println("buggy setter left field as: " + s.value);

        Order quick = new Order();
        System.out.println(quick.summary());
    }
}

class Order {
    private final String id;
    private final List<String> lines = new ArrayList<>();
    private int itemCount;

    Order() {
        this("DRAFT");                        // use 2: constructor chaining
    }

    Order(String id) {
        this.id = id;                          // use 1: field vs parameter
    }

    Order add(String product, int quantity) {
        lines.add(quantity + " x " + product);
        itemCount += quantity;
        AuditLog.record(this, "added " + product); // use 3: pass self along
        return this;                           // use 4: enable chaining
    }

    String id() {
        return id;
    }

    String summary() {
        return "order " + id + ": " + lines + " (" + itemCount + " items)";
    }
}

class AuditLog {
    static void record(Order order, String event) {
        System.out.println("audit " + order.id() + ": " + event);
    }
}

class Shadowing {
    String value;

    Shadowing(String value) {
        value = value;   // assigns the parameter to itself; the field stays null
    }
}

/* Output:
audit A-100: added keyboard
audit A-100: added mouse
audit A-100: added monitor
order A-100: [2 x keyboard, 1 x mouse, 1 x monitor] (4 items)
buggy setter left field as: null
order DRAFT: [] (0 items)
*/
