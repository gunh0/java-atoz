// EnumMap is a Map whose keys are constants of one enum. It is backed by a
// plain array indexed by ordinal(), so get/put are array accesses, there is
// no hashing, and iteration follows declaration order.
//  - prefer it to HashMap<SomeEnum, V>, and to arrays indexed by ordinal()
//    (which compile but lose type safety and break when constants move)
//  - null keys are rejected; null values are allowed
//  - Collectors.groupingBy(f, () -> new EnumMap<>(K.class), downstream)
//    builds one from a stream
//  - nested EnumMaps model two-dimensional lookups, e.g. a transition table
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EnumMapExample {
    enum Severity { LOW, MEDIUM, HIGH, CRITICAL }

    enum Status { OPEN, TRIAGED, FIXED, CLOSED }

    enum Action { TRIAGE, FIX, VERIFY, REOPEN }

    record Finding(String id, Severity severity, Status status) {}

    public static void main(String[] args) {
        // Fixed policy: SLA days per severity
        Map<Severity, Integer> slaDays = new EnumMap<>(Severity.class);
        slaDays.put(Severity.CRITICAL, 2);
        slaDays.put(Severity.LOW, 90);
        slaDays.put(Severity.HIGH, 14);
        slaDays.put(Severity.MEDIUM, 30);
        System.out.println("SLA days: " + slaDays); // declaration order, not insertion order

        List<Finding> findings = List.of(
                new Finding("F-1", Severity.HIGH, Status.OPEN),
                new Finding("F-2", Severity.LOW, Status.CLOSED),
                new Finding("F-3", Severity.CRITICAL, Status.TRIAGED),
                new Finding("F-4", Severity.HIGH, Status.FIXED),
                new Finding("F-5", Severity.HIGH, Status.OPEN));

        // Counting into an EnumMap with a stream
        Map<Severity, Long> bySeverity = findings.stream().collect(
                Collectors.groupingBy(Finding::severity, () -> new EnumMap<>(Severity.class), Collectors.counting()));
        System.out.println("count by severity: " + bySeverity);

        // Make sure every severity appears, even with zero findings
        Map<Severity, Long> report = new EnumMap<>(Severity.class);
        for (Severity s : Severity.values()) report.put(s, bySeverity.getOrDefault(s, 0L));
        System.out.println("full report: " + report);

        // A two-level EnumMap as a state transition table
        Map<Status, Map<Action, Status>> transitions = new EnumMap<>(Status.class);
        for (Status s : Status.values()) transitions.put(s, new EnumMap<>(Action.class));
        transitions.get(Status.OPEN).put(Action.TRIAGE, Status.TRIAGED);
        transitions.get(Status.TRIAGED).put(Action.FIX, Status.FIXED);
        transitions.get(Status.FIXED).put(Action.VERIFY, Status.CLOSED);
        transitions.get(Status.FIXED).put(Action.REOPEN, Status.OPEN);
        transitions.get(Status.CLOSED).put(Action.REOPEN, Status.OPEN);

        Status current = Status.OPEN;
        for (Action a : List.of(Action.TRIAGE, Action.FIX, Action.REOPEN, Action.VERIFY)) {
            Status next = transitions.get(current).get(a);
            System.out.printf("%-7s + %-6s -> %s%n", current, a, next == null ? "not allowed" : next);
            if (next != null) current = next;
        }

        try {
            slaDays.put(null, 1);
        } catch (NullPointerException e) {
            System.out.println("put(null, ...) -> NullPointerException");
        }
    }
}

/* Output:
SLA days: {LOW=90, MEDIUM=30, HIGH=14, CRITICAL=2}
count by severity: {LOW=1, HIGH=3, CRITICAL=1}
full report: {LOW=1, MEDIUM=0, HIGH=3, CRITICAL=1}
OPEN    + TRIAGE -> TRIAGED
TRIAGED + FIX    -> FIXED
FIXED   + REOPEN -> OPEN
OPEN    + VERIFY -> not allowed
put(null, ...) -> NullPointerException
*/
