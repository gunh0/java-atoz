// Optional is most useful when you chain operations on it instead of
// unwrapping early. Each step is skipped once the Optional is empty:
//   map(f)       transform the value (a null result becomes empty)
//   flatMap(f)   f already returns an Optional; avoids Optional<Optional<T>>
//   filter(p)    keep the value only if it matches
//   or(supplier) fall back to another Optional (Java 9)
//   stream()     0 or 1 element stream, handy for flattening many Optionals
// This replaces nested `if (x != null && x.getY() != null ...)` chains.
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OptionalChainingExample {
    record Address(String city) {}

    record Profile(String displayName, Address address) {
        Optional<Address> addressOpt() {
            return Optional.ofNullable(address);
        }
    }

    record Account(String id, Profile profile) {}

    static final Map<String, Account> ACCOUNTS = Map.of(
            "a1", new Account("a1", new Profile("Alice", new Address("Seoul"))),
            "b2", new Account("b2", new Profile("Bob", null)),
            "c3", new Account("c3", null));

    static Optional<Account> find(String id) {
        return Optional.ofNullable(ACCOUNTS.get(id));
    }

    // map for nullable getters, flatMap for methods that return Optional
    static String city(String id) {
        return find(id)
                .map(Account::profile)
                .flatMap(Profile::addressOpt)
                .map(Address::city)
                .orElse("(unknown)");
    }

    public static void main(String[] args) {
        for (String id : List.of("a1", "b2", "c3", "zz")) {
            System.out.println("city(" + id + ") = " + city(id));
        }

        // map vs flatMap: map would nest the Optionals
        Optional<Optional<Address>> nested = find("a1").map(Account::profile).map(Profile::addressOpt);
        Optional<Address> flat = find("a1").map(Account::profile).flatMap(Profile::addressOpt);
        System.out.println("map     -> " + nested);
        System.out.println("flatMap -> " + flat);

        // filter: validate a configured value
        Map<String, String> env = Map.of("PORT", "70000", "ADMIN_PORT", "8443");
        for (String key : List.of("PORT", "ADMIN_PORT", "DEBUG_PORT")) {
            Optional<Integer> port = Optional.ofNullable(env.get(key))
                    .filter(s -> s.matches("\\d{1,5}"))
                    .map(Integer::parseInt)
                    .filter(p -> p > 0 && p <= 65535);
            System.out.println(key + " -> " + port.map(String::valueOf).orElse("invalid or missing"));
        }

        // or(): try a sequence of sources, lazily
        Optional<String> fromFlag = Optional.empty();
        Optional<String> token = fromFlag
                .or(() -> Optional.ofNullable(env.get("TOKEN")))
                .or(() -> Optional.of("token-from-keychain"));
        System.out.println("token source = " + token.get());

        // stream(): keep only the present values
        List<String> names = List.of("a1", "zz", "b2", "c3").stream()
                .map(OptionalChainingExample::find)
                .flatMap(Optional::stream)
                .map(Account::profile)
                .flatMap(p -> Optional.ofNullable(p).stream())
                .map(Profile::displayName)
                .toList();
        System.out.println("display names = " + names);
    }
}

/* Output:
city(a1) = Seoul
city(b2) = (unknown)
city(c3) = (unknown)
city(zz) = (unknown)
map     -> Optional[Optional[Address[city=Seoul]]]
flatMap -> Optional[Address[city=Seoul]]
PORT -> invalid or missing
ADMIN_PORT -> 8443
DEBUG_PORT -> invalid or missing
token source = token-from-keychain
display names = [Alice, Bob]
*/
