// The java.util.function interfaces carry default methods that build new
// functions out of existing ones, so small, named pieces can be combined
// instead of writing one large lambda:
//   Function:  f.andThen(g) = g(f(x))      f.compose(g) = f(g(x))
//              Function.identity()
//   Predicate: p.and(q)  p.or(q)  p.negate()  Predicate.not(p)  isEqual(x)
//   Consumer:  c.andThen(d)  (run c, then d, on the same value)
// Comparator composes the same way (comparing().thenComparing().reversed());
// see Collections/Comparator for that.
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class CompositionExample {
    public static void main(String[] args) {
        // A small input-normalization pipeline from reusable steps
        Function<String, String> trim = String::strip;
        Function<String, String> lower = s -> s.toLowerCase();
        Function<String, String> collapseDots = s -> s.replaceAll("\\.+", ".");
        Function<String, String> normalizeHost = trim.andThen(lower).andThen(collapseDots);
        System.out.println("normalize(\"  Login..Example.COM \") = " + normalizeHost.apply("  Login..Example.COM "));

        // andThen vs compose: the order of application differs
        Function<Integer, Integer> plus1 = x -> x + 1;
        Function<Integer, Integer> times10 = x -> x * 10;
        System.out.println("plus1.andThen(times10)(4) = " + plus1.andThen(times10).apply(4));
        System.out.println("plus1.compose(times10)(4) = " + plus1.compose(times10).apply(4));
        System.out.println("identity()(\"x\") = " + Function.<String>identity().apply("x"));

        // Predicates combined into a password policy
        Predicate<String> longEnough = p -> p.length() >= 12;
        Predicate<String> hasDigit = p -> p.chars().anyMatch(Character::isDigit);
        Predicate<String> hasUpper = p -> p.chars().anyMatch(Character::isUpperCase);
        Predicate<String> isCommon = Predicate.isEqual("Password1234");
        Predicate<String> strong = longEnough.and(hasDigit).and(hasUpper).and(isCommon.negate());
        for (String pw : List.of("hunter2", "Password1234", "correcthorsebattery", "Tr0ub4dor&3xyz")) {
            System.out.printf("%-20s strong=%s%n", pw, strong.test(pw));
        }

        // Predicate.not reads well in method-reference pipelines
        List<String> lines = List.of("allow 10.0.0.0/8", "", "deny all", "  ");
        List<String> rules = lines.stream().filter(Predicate.not(String::isBlank)).toList();
        System.out.println("non-blank rules: " + rules);

        // Consumer.andThen: two side effects on the same value
        List<String> audit = new ArrayList<>();
        Consumer<String> print = e -> System.out.println("event: " + e);
        Consumer<String> record = audit::add;
        print.andThen(record).accept("user alice logged in");
        System.out.println("audit = " + audit);

        // andThen on a UnaryOperator returns a plain Function
        UnaryOperator<String> mask = s -> s.replaceAll("\\d(?=\\d{4})", "*");
        System.out.println("mask card = " + mask.andThen(s -> "[" + s + "]").apply("4111111111111111"));

        // Side note: Comparator composes too
        List<String> users = new ArrayList<>(List.of("bob", "alice", "eve", "dave"));
        users.sort(Comparator.comparing(String::length).thenComparing(Comparator.reverseOrder()));
        System.out.println("by length, then reverse alphabetical: " + users);
    }
}

/* Output:
normalize("  Login..Example.COM ") = login.example.com
plus1.andThen(times10)(4) = 50
plus1.compose(times10)(4) = 41
identity()("x") = x
hunter2              strong=false
Password1234         strong=false
correcthorsebattery  strong=false
Tr0ub4dor&3xyz       strong=true
non-blank rules: [allow 10.0.0.0/8, deny all]
event: user alice logged in
audit = [user alice logged in]
mask card = [************1111]
by length, then reverse alphabetical: [eve, bob, dave, alice]
*/
