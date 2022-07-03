// Effective Java, Item 17: Minimize mutability.
//
// An immutable object has exactly one state for its whole life, so it is
// thread-safe without locks, can be shared and cached freely, and makes a
// perfect map key or set element. The recipe: no mutators, all fields
// private final, no way to subclass (final class or private constructor with
// static factories), and no access to mutable internals. Operations return a
// new instance instead of changing this one ("functional" style, e.g. plus
// rather than add). The cost is one object per distinct value; offer a
// mutable companion (String has StringBuilder) for multistep work.
import java.util.HashSet;
import java.util.Set;

public class Immutability {
    public static void main(String[] args) {
        Money price = Money.of(1999, "KRW");
        Money total = price.times(3).plus(Money.of(500, "KRW"));
        System.out.println("price unchanged: " + price + ", total: " + total);

        // Static factory can hand out shared instances
        System.out.println("Money.of(0, KRW) is cached: " + (Money.of(0, "KRW") == Money.of(0, "KRW")));

        // Safe as a hash key: its hashCode can never change
        Set<Money> seen = new HashSet<>();
        seen.add(Money.of(100, "USD"));
        System.out.println("found after use: " + seen.contains(Money.of(50, "USD").times(2)));

        // A mutable key breaks the set once it changes
        Set<MutableMoney> mutableSeen = new HashSet<>();
        MutableMoney key = new MutableMoney(100, "USD");
        mutableSeen.add(key);
        key.amount = 200;
        System.out.println("mutable key still found: " + mutableSeen.contains(key));

        // A non-final "immutable" class can be subclassed into something mutable
        NonFinalMoney sneaky = new SneakyMoney(100);
        System.out.println("non-final value read twice: " + sneaky.amount() + ", " + sneaky.amount());

        // Mutable companion for multistep operations
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(i);
        }
        System.out.println("built with the mutable companion: " + sb);
    }
}

final class Money {
    private static final Money ZERO_KRW = new Money(0, "KRW");

    private final long amount;
    private final String currency;

    private Money(long amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    static Money of(long amount, String currency) {
        if (amount == 0 && currency.equals("KRW")) {
            return ZERO_KRW;
        }
        return new Money(amount, currency);
    }

    Money plus(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("currency mismatch");
        }
        return of(Math.addExact(amount, other.amount), currency);
    }

    Money times(long factor) {
        return of(Math.multiplyExact(amount, factor), currency);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Money m && m.amount == amount && m.currency.equals(currency);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(amount) * 31 + currency.hashCode();
    }

    @Override
    public String toString() {
        return amount + " " + currency;
    }
}

class MutableMoney {
    long amount;
    final String currency;

    MutableMoney(long amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MutableMoney m && m.amount == amount && m.currency.equals(currency);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(amount) * 31 + currency.hashCode();
    }
}

class NonFinalMoney {
    private final long amount;

    NonFinalMoney(long amount) {
        this.amount = amount;
    }

    long amount() {
        return amount;
    }
}

class SneakyMoney extends NonFinalMoney {
    private long drift;

    SneakyMoney(long amount) {
        super(amount);
    }

    @Override
    long amount() {
        return super.amount() + drift++;
    }
}

/* Output:
price unchanged: 1999 KRW, total: 6497 KRW
Money.of(0, KRW) is cached: true
found after use: true
mutable key still found: false
non-final value read twice: 100, 101
built with the mutable companion: 01234
*/
