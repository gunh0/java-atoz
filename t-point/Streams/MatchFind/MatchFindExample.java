// Terminal operations that answer a question about the stream:
//   anyMatch / allMatch / noneMatch(p)   boolean, short-circuiting
//   findFirst() / findAny()              Optional, short-circuiting
//   min(c) / max(c)                      Optional, empty for an empty stream
//   count()                              long
// On an EMPTY stream allMatch and noneMatch are true and anyMatch is false
// ("vacuous truth"), which is easy to get wrong in validation code.
// findAny may return any element (it is allowed to differ on parallel
// streams); use findFirst when the order matters.
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class MatchFindExample {
    record Session(String user, String ip, int minutesIdle, boolean mfa) {}

    public static void main(String[] args) {
        List<Session> sessions = List.of(
                new Session("alice", "10.0.0.5", 3, true),
                new Session("bob", "203.0.113.9", 42, false),
                new Session("carol", "10.0.0.8", 17, true),
                new Session("dave", "10.0.0.9", 0, true));

        System.out.println("any without MFA?   " + sessions.stream().anyMatch(s -> !s.mfa()));
        System.out.println("all from 10/8?     " + sessions.stream().allMatch(s -> s.ip().startsWith("10.")));
        System.out.println("none idle > 60 m?  " + sessions.stream().noneMatch(s -> s.minutesIdle() > 60));

        Optional<Session> firstExternal = sessions.stream().filter(s -> !s.ip().startsWith("10.")).findFirst();
        System.out.println("first external: " + firstExternal.map(Session::user).orElse("none"));

        Comparator<Session> byIdle = Comparator.comparingInt(Session::minutesIdle);
        System.out.println("most idle:  " + sessions.stream().max(byIdle).map(Session::user).orElseThrow());
        System.out.println("least idle: " + sessions.stream().min(byIdle).map(Session::user).orElseThrow());
        System.out.println("idle > 10 m: " + sessions.stream().filter(s -> s.minutesIdle() > 10).count());

        // Empty streams
        List<Session> none = List.of();
        System.out.println("empty allMatch(mfa)  = " + none.stream().allMatch(Session::mfa));
        System.out.println("empty noneMatch(mfa) = " + none.stream().noneMatch(Session::mfa));
        System.out.println("empty anyMatch(mfa)  = " + none.stream().anyMatch(Session::mfa));
        System.out.println("empty max            = " + none.stream().max(byIdle));

        // A check like "every admin uses MFA" passes trivially when there are no admins
        List<Session> admins = sessions.stream().filter(s -> s.user().equals("root")).toList();
        boolean policyOk = admins.stream().allMatch(Session::mfa);
        System.out.println("all root sessions use MFA? " + policyOk + " (" + admins.size() + " root sessions)");

        // Short-circuiting: anyMatch stops at the first hit
        int[] checked = {0};
        sessions.stream().peek(s -> checked[0]++).anyMatch(s -> !s.mfa());
        System.out.println("anyMatch examined " + checked[0] + " of " + sessions.size() + " sessions");
    }
}

/* Output:
any without MFA?   true
all from 10/8?     false
none idle > 60 m?  true
first external: bob
most idle:  bob
least idle: dave
idle > 10 m: 2
empty allMatch(mfa)  = true
empty noneMatch(mfa) = true
empty anyMatch(mfa)  = false
empty max            = Optional.empty
all root sessions use MFA? true (0 root sessions)
anyMatch examined 2 of 4 sessions
*/
