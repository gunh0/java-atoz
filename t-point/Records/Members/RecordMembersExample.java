// Beyond the generated parts (private final fields, canonical constructor,
// accessors, equals/hashCode/toString), a record body may declare:
//  - static fields, static factory methods and nested types
//  - instance methods (derived values), and explicit accessors that override
//    the generated ones (they must stay public and return the component type)
//  - implementations of interfaces (a record cannot extend a class: it
//    already extends java.lang.Record, and it is implicitly final)
// It may not declare extra instance fields. Records can also be declared
// locally inside a method, handy for intermediate results in a stream.
// equals compares components with their own equals: arrays compare by identity.
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class RecordMembersExample {

    record Money(long cents, String currency) implements Comparable<Money> {
        static final Money ZERO_EUR = new Money(0, "EUR");

        // Static factory with a more natural input format
        static Money of(String amount, String currency) {
            long cents = new java.math.BigDecimal(amount).movePointRight(2).longValueExact();
            return new Money(cents, currency);
        }

        Money plus(Money other) {
            if (!currency.equals(other.currency)) {
                throw new IllegalArgumentException("currency mismatch");
            }
            return new Money(cents + other.cents, currency);
        }

        // Override the generated toString for a domain-friendly format
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%d.%02d %s", cents / 100, cents % 100, currency);
        }

        @Override
        public int compareTo(Money o) {
            return Long.compare(cents, o.cents);
        }
    }

    record Tag(String name) {
        // Explicit accessor: still public, same return type
        @Override
        public String name() {
            return "#" + name;
        }
    }

    record Packet(int id, byte[] payload) { }

    public static void main(String[] args) {
        Money price = Money.of("19.99", "EUR");
        Money shipping = Money.of("4.50", "EUR");
        System.out.println("total: " + price.plus(shipping));
        System.out.println("zero:  " + Money.ZERO_EUR);
        System.out.println("max:   " + List.of(price, shipping, Money.ZERO_EUR).stream()
                .max(Comparator.naturalOrder()).orElseThrow());
        System.out.println("is a Record: " + (price instanceof Record));
        System.out.println("components: " + Arrays.stream(Money.class.getRecordComponents())
                .map(c -> c.getType().getSimpleName() + " " + c.getName()).toList());

        Tag tag = new Tag("java");
        System.out.println("accessor: " + tag.name() + ", toString: " + tag); // toString uses the field

        // equals/hashCode are value-based...
        System.out.println("Money equals: " + Money.of("1.00", "USD").equals(new Money(100, "USD")));
        // ...except for array components, which compare by reference
        Packet p1 = new Packet(1, new byte[] {1, 2});
        Packet p2 = new Packet(1, new byte[] {1, 2});
        System.out.println("Packet equals with equal arrays: " + p1.equals(p2));

        // A local record names an intermediate tuple inside one method
        record WordLength(String word, int length) { }
        List<String> words = List.of("record", "sealed", "switch", "pattern", "var");
        List<WordLength> longest = words.stream()
                .map(w -> new WordLength(w, w.length()))
                .sorted(Comparator.comparingInt(WordLength::length).reversed()
                        .thenComparing(WordLength::word))
                .limit(3)
                .toList();
        System.out.println("longest: " + longest);
    }
}

/* Output:
total: 24.49 EUR
zero:  0.00 EUR
max:   19.99 EUR
is a Record: true
components: [long cents, String currency]
accessor: #java, toString: Tag[name=java]
Money equals: true
Packet equals with equal arrays: false
longest: [WordLength[word=pattern, length=7], WordLength[word=record, length=6], WordLength[word=sealed, length=6]]
*/
