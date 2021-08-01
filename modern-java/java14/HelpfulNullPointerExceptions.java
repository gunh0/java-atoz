// JEP 358 — Helpful NullPointerExceptions (Java 14)
//
// A plain NullPointerException only gave a line number. On a line like
// a.b.c.i = 99 or orders[i].customer().name() you could not tell WHICH
// reference was null. Since JEP 358 the JVM analyzes the failing bytecode and
// describes both the action that failed and the null expression. Added in
// Java 14 behind -XX:+ShowCodeDetailsInExceptionMessages, enabled by default
// since Java 15. Local variable names appear only if the class was compiled
// with -g; fields, array elements and method calls are always described.
public class HelpfulNullPointerExceptions {
    static class Address {
        String city;
    }

    static class Customer {
        Address address;
        String name;

        Address address() {
            return address;
        }
    }

    static class Order {
        Customer customer;
    }

    static Order[] orders = new Order[2];

    public static void main(String[] args) {
        orders[0] = new Order();
        orders[0].customer = new Customer();
        // orders[0].customer.address is null; orders[1] is null

        attempt("field chain", () -> System.out.println(orders[0].customer.address.city.length()));
        attempt("method call result", () -> System.out.println(orders[0].customer.address().city));
        attempt("array element", () -> System.out.println(orders[1].customer));
        attempt("null array", () -> {
            int[] counts = null;
            Holder.counts = counts;
            System.out.println(Holder.counts[0]);
        });
        attempt("unboxing", () -> {
            Holder.boxed = null;
            int value = Holder.boxed;
            System.out.println(value);
        });
        attempt("synchronized on null", () -> {
            synchronized (Holder.lock) {
                System.out.println("unreachable");
            }
        });
        attempt("explicit throw", () -> {
            throw new NullPointerException("custom message is kept as is");
        });
    }

    static class Holder {
        static int[] counts;
        static Integer boxed;
        static Object lock;
    }

    private static void attempt(String label, Runnable action) {
        try {
            action.run();
        } catch (NullPointerException e) {
            System.out.println(label + ": " + e.getMessage());
        }
    }
}

/* Output:
field chain: Cannot read field "city" because "HelpfulNullPointerExceptions.orders[0].customer.address" is null
method call result: Cannot read field "city" because the return value of "HelpfulNullPointerExceptions$Customer.address()" is null
array element: Cannot read field "customer" because "HelpfulNullPointerExceptions.orders[1]" is null
null array: Cannot load from int array because "HelpfulNullPointerExceptions$Holder.counts" is null
unboxing: Cannot invoke "java.lang.Integer.intValue()" because "HelpfulNullPointerExceptions$Holder.boxed" is null
synchronized on null: Cannot enter synchronized block because "HelpfulNullPointerExceptions$Holder.lock" is null
explicit throw: custom message is kept as is
*/
