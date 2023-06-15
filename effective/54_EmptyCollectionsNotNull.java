// Effective Java, Item 54: Return empty collections or arrays, not nulls.
//
// Returning null for "nothing" forces every caller to write a special case,
// and the one caller who forgets gets a NullPointerException, perhaps years
// later when the list is finally empty in production. An empty collection
// or array works with every loop and stream unchanged. The "it saves an
// allocation" argument rarely holds: allocation is cheap, and if it matters
// you can return the same immutable empty collection (List.of(),
// Collections.emptyList()) or a shared zero-length array every time.
import java.util.ArrayList;
import java.util.List;

public class EmptyCollectionsNotNull {
    public static void main(String[] args) {
        NullCheeseShop nullShop = new NullCheeseShop();
        CheeseShop shop = new CheeseShop();

        // Caller of the null-returning API must remember the special case
        List<String> inStock = nullShop.getCheeses();
        if (inStock != null && inStock.contains("stilton")) {
            System.out.println("stilton available");
        } else {
            System.out.println("null shop: guarded check works");
        }
        try {
            System.out.println("null shop: " + nullShop.getCheeses().size() + " cheeses");
        } catch (NullPointerException e) {
            System.out.println("null shop: forgot the check -> NullPointerException");
        }

        // Empty collection: no special case anywhere
        System.out.println("empty shop: " + shop.getCheeses().size() + " cheeses, stilton? "
                + shop.getCheeses().contains("stilton"));
        System.out.println("empty shop array length: " + shop.getCheeseArray().length);
        System.out.println("same empty array every time? " + (shop.getCheeseArray() == shop.getCheeseArray()));

        shop.add("cheddar");
        System.out.println("after delivery: " + shop.getCheeses() + ", array length "
                + shop.getCheeseArray().length);
    }
}

class NullCheeseShop {
    private final List<String> cheesesInStock = new ArrayList<>();

    /** @return the cheeses in stock, or null if there are none (don't do this) */
    List<String> getCheeses() {
        return cheesesInStock.isEmpty() ? null : new ArrayList<>(cheesesInStock);
    }
}

class CheeseShop {
    private static final String[] EMPTY_CHEESE_ARRAY = new String[0];
    private final List<String> cheesesInStock = new ArrayList<>();

    void add(String cheese) {
        cheesesInStock.add(cheese);
    }

    /** @return the cheeses in stock; empty, never null */
    List<String> getCheeses() {
        return cheesesInStock.isEmpty() ? List.of() : List.copyOf(cheesesInStock);
    }

    /** toArray reuses the zero-length array when the list is empty */
    String[] getCheeseArray() {
        return cheesesInStock.toArray(EMPTY_CHEESE_ARRAY);
    }
}

/* Output:
null shop: guarded check works
null shop: forgot the check -> NullPointerException
empty shop: 0 cheeses, stilton? false
empty shop array length: 0
same empty array every time? true
after delivery: [cheddar], array length 1
*/
