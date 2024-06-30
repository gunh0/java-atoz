// A sealed class or interface lists the only types allowed to extend it:
// `sealed interface Shape permits Circle, Square, Polygon`. Each permitted
// subtype must say how it continues the hierarchy:
//  - final       no further subclasses (records are implicitly final)
//  - sealed      its own closed list of subclasses
//  - non-sealed  open again for anyone to extend
// Because the compiler knows every subtype, a switch over a sealed type is
// exhaustive without a default branch, and adding a new permitted subtype
// turns every switch that forgot it into a compile error instead of a
// runtime surprise. In one source file, permits can be inferred.
import java.util.List;
import java.util.Locale;

public class SealedHierarchyExample {

    // An authentication result: exactly these outcomes, nothing else
    sealed interface AuthResult permits Success, Failure, Challenge { }

    record Success(String user, List<String> roles) implements AuthResult { }

    // A sealed intermediate type with its own closed set of subtypes
    sealed interface Failure extends AuthResult permits BadCredentials, Locked { }

    record BadCredentials(String user, int attempts) implements Failure { }

    record Locked(String user, int minutes) implements Failure { }

    // non-sealed: any MFA mechanism may extend it
    static non-sealed class Challenge implements AuthResult {
        String method() {
            return "generic";
        }
    }

    static final class TotpChallenge extends Challenge {
        @Override
        String method() {
            return "totp";
        }
    }

    // Exhaustive: no default needed, and a new AuthResult subtype would not compile here
    static String httpResponse(AuthResult result) {
        return switch (result) {
            case Success s -> "200 welcome " + s.user() + " " + s.roles();
            case BadCredentials b when b.attempts() >= 3 -> "401 bad credentials, captcha required";
            case BadCredentials b -> "401 bad credentials";
            case Locked l -> String.format(Locale.ROOT, "423 locked for %d min", l.minutes());
            case Challenge c -> "401 second factor required: " + c.method();
        };
    }

    // Switching on the sealed intermediate type is exhaustive too
    static boolean shouldAlert(Failure failure) {
        return switch (failure) {
            case BadCredentials(var user, var attempts) -> attempts >= 5;
            case Locked l -> true;
        };
    }

    public static void main(String[] args) {
        List<AuthResult> results = List.of(
                new Success("alice", List.of("admin")),
                new BadCredentials("bob", 1),
                new BadCredentials("bob", 4),
                new Locked("mallory", 15),
                new Challenge(),
                new TotpChallenge());
        for (AuthResult r : results) {
            System.out.println(httpResponse(r));
        }

        System.out.println("alert(4 attempts): " + shouldAlert(new BadCredentials("bob", 4)));
        System.out.println("alert(6 attempts): " + shouldAlert(new BadCredentials("bob", 6)));
        System.out.println("alert(locked):     " + shouldAlert(new Locked("eve", 5)));

        // Reflection exposes the closed set
        System.out.println("AuthResult sealed: " + AuthResult.class.isSealed());
        for (Class<?> c : AuthResult.class.getPermittedSubclasses()) {
            System.out.println("  permits " + c.getSimpleName());
        }
        System.out.println("Challenge sealed: " + Challenge.class.isSealed());
    }
}

/* Output:
200 welcome alice [admin]
401 bad credentials
401 bad credentials, captcha required
423 locked for 15 min
401 second factor required: generic
401 second factor required: totp
alert(4 attempts): false
alert(6 attempts): true
alert(locked):     true
AuthResult sealed: true
  permits Success
  permits Failure
  permits Challenge
Challenge sealed: false
*/
