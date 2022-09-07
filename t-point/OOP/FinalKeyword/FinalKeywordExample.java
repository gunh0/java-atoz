// `final` means "cannot be changed after this point", applied to:
//  - a variable or field: assigned exactly once (a blank final field must be
//    assigned in every constructor)
//  - a method: cannot be overridden by subclasses
//  - a class: cannot be extended (String, Integer, ... are final)
// A final reference cannot be re-pointed, but the object it points to can
// still be mutated: final is not the same as immutable.
import java.util.ArrayList;
import java.util.List;

public class FinalKeywordExample {
    static final double VAT_RATE = 0.10;    // a constant: static + final

    public static void main(String[] args) {
        final int limit = 3;
        // limit = 4;                       // compile error: already assigned

        Invoice invoice = new Invoice("INV-7");
        invoice.addLine("license", 1200);
        invoice.addLine("support", 300);
        System.out.println(invoice.total());

        // final reference, mutable object
        final List<String> tags = new ArrayList<>();
        tags.add("paid");                   // allowed: the list itself changes
        // tags = new ArrayList<>();        // compile error: the reference is final
        System.out.println("tags: " + tags + ", limit: " + limit);

        // A final method keeps its behaviour in every subclass
        DiscountedInvoice d = new DiscountedInvoice("INV-8");
        d.addLine("license", 1000);
        System.out.println(d.total());

        // Final classes refuse subclasses; reflection shows the modifier
        System.out.println("String is final: "
                + java.lang.reflect.Modifier.isFinal(String.class.getModifiers()));
        System.out.println("TaxCode is final: "
                + java.lang.reflect.Modifier.isFinal(TaxCode.class.getModifiers()));
    }
}

class Invoice {
    private final String id;            // blank final, set once in the constructor
    private final List<Integer> amounts = new ArrayList<>();

    Invoice(String id) {
        this.id = id;
    }

    void addLine(String item, int amount) {
        amounts.add(amount);
    }

    int net() {
        return amounts.stream().mapToInt(Integer::intValue).sum();
    }

    // Subclasses may change net(), but not how the total is computed
    final String total() {
        int net = net();
        long vat = Math.round(net * FinalKeywordExample.VAT_RATE);
        return id + ": net=" + net + " vat=" + vat + " total=" + (net + vat);
    }
}

class DiscountedInvoice extends Invoice {
    DiscountedInvoice(String id) {
        super(id);
    }

    @Override
    int net() {
        return super.net() * 90 / 100;
    }

    // String total() { ... }  would not compile: total() is final in Invoice
}

final class TaxCode {
    // class Special extends TaxCode {}  would not compile: TaxCode is final
}

/* Output:
INV-7: net=1500 vat=150 total=1650
tags: [paid], limit: 3
INV-8: net=900 vat=90 total=990
String is final: true
TaxCode is final: true
*/
