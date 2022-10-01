// An enum is a class with a fixed set of instances, its constants. Beyond
// plain names it can carry:
//  - fields, set through a (always private) constructor
//  - methods, including abstract ones that each constant implements in its
//    own body (constant-specific behaviour)
// Every enum gets values(), valueOf(String), name() and ordinal() for free,
// and works in switch. Do not persist ordinal(): it changes when constants
// are reordered; store name() or a field instead.
import java.util.EnumMap;
import java.util.Map;

public class EnumExample {
    public static void main(String[] args) {
        // Iterate over all constants with their fields
        for (Plan plan : Plan.values()) {
            System.out.printf("%-10s ordinal=%d price=%5.2f maxUsers=%d%n",
                    plan, plan.ordinal(), plan.monthlyPrice(), plan.maxUsers());
        }

        // valueOf maps a name back to the constant (exact, case-sensitive)
        Plan chosen = Plan.valueOf("TEAM");
        System.out.println("valueOf(\"TEAM\") = " + chosen + ", annual price = " + chosen.annualPrice());
        try {
            Plan.valueOf("team");
        } catch (IllegalArgumentException e) {
            System.out.println("valueOf(\"team\") -> IllegalArgumentException");
        }

        // Constant-specific method bodies
        for (Operation op : Operation.values()) {
            System.out.println("8 " + op.symbol() + " 2 = " + op.apply(8, 2));
        }

        // Enums in switch (no qualifier needed on the case labels)
        System.out.println(supportLevel(Plan.FREE));
        System.out.println(supportLevel(Plan.ENTERPRISE));

        // EnumMap: a compact map keyed by an enum, ordered by declaration
        Map<Plan, Integer> subscribers = new EnumMap<>(Plan.class);
        subscribers.put(Plan.TEAM, 12);
        subscribers.put(Plan.FREE, 340);
        System.out.println("subscribers: " + subscribers);

        // Enum constants are singletons: == is the right comparison
        System.out.println("chosen == Plan.TEAM: " + (chosen == Plan.TEAM));
    }

    static String supportLevel(Plan plan) {
        return switch (plan) {
            case FREE -> "community forum";
            case TEAM -> "email, next business day";
            case ENTERPRISE -> "24/7 phone";
        };
    }
}

enum Plan {
    FREE(0, 3),
    TEAM(12.5, 50),
    ENTERPRISE(49.0, Integer.MAX_VALUE);

    private final double monthlyPrice;
    private final int maxUsers;

    Plan(double monthlyPrice, int maxUsers) {   // implicitly private
        this.monthlyPrice = monthlyPrice;
        this.maxUsers = maxUsers;
    }

    double monthlyPrice() { return monthlyPrice; }
    int maxUsers() { return maxUsers; }

    double annualPrice() {
        return monthlyPrice * 12 * 0.9;         // ten percent off when paid yearly
    }
}

enum Operation {
    PLUS("+") {
        @Override int apply(int a, int b) { return a + b; }
    },
    MINUS("-") {
        @Override int apply(int a, int b) { return a - b; }
    },
    TIMES("*") {
        @Override int apply(int a, int b) { return a * b; }
    },
    DIVIDE("/") {
        @Override int apply(int a, int b) { return a / b; }
    };

    private final String symbol;

    Operation(String symbol) {
        this.symbol = symbol;
    }

    String symbol() {
        return symbol;
    }

    abstract int apply(int a, int b);           // each constant must implement it
}

/* Output:
FREE       ordinal=0 price= 0.00 maxUsers=3
TEAM       ordinal=1 price=12.50 maxUsers=50
ENTERPRISE ordinal=2 price=49.00 maxUsers=2147483647
valueOf("TEAM") = TEAM, annual price = 135.0
valueOf("team") -> IllegalArgumentException
8 + 2 = 10
8 - 2 = 6
8 * 2 = 16
8 / 2 = 4
community forum
24/7 phone
subscribers: {FREE=340, TEAM=12}
chosen == Plan.TEAM: true
*/
