// Effective Java, Item 71: Avoid unnecessary use of checked exceptions.
//
// A checked exception is a burden: every caller must catch or propagate it,
// and methods that throw one cannot be used directly in lambdas and streams.
// It is only worth that cost when the failure cannot be prevented by proper
// use of the API *and* the caller can do something useful about it. If the
// best a caller can do is `throw new AssertionError()` or print a stack trace,
// use an unchecked exception. Often the cleanest fix is to return an
// Optional (when the caller needs no detail about the failure) or to split
// the method into a state-testing method plus an action.
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AvoidUnnecessaryCheckedExceptions {
    static class InvalidPortException extends Exception {
        InvalidPortException(String s) {
            super("invalid port: " + s);
        }
    }

    // Before: a checked exception nobody can meaningfully handle
    static int parsePortChecked(String s) throws InvalidPortException {
        try {
            int port = Integer.parseInt(s);
            if (port < 1 || port > 65535) {
                throw new InvalidPortException(s);
            }
            return port;
        } catch (NumberFormatException e) {
            throw new InvalidPortException(s);
        }
    }

    // After (1): Optional return, composes with streams
    static Optional<Integer> parsePort(String s) {
        try {
            int port = Integer.parseInt(s);
            return port >= 1 && port <= 65535 ? Optional.of(port) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    // After (2): state-testing method + action that throws unchecked
    static boolean isValidPort(String s) {
        return parsePort(s).isPresent();
    }

    static int port(String s) {
        return parsePort(s).orElseThrow(() -> new IllegalArgumentException("invalid port: " + s));
    }

    public static void main(String[] args) {
        List<String> inputs = List.of("8080", "http", "70000", "443");

        // The checked version cannot be used in map(); it needs a wrapper lambda
        List<String> checked = inputs.stream().map(s -> {
            try {
                return String.valueOf(parsePortChecked(s));
            } catch (InvalidPortException e) {
                return "-";
            }
        }).collect(Collectors.toList());
        System.out.println("checked, with try/catch in lambda: " + checked);

        // The Optional version just composes
        List<Integer> valid = inputs.stream().map(AvoidUnnecessaryCheckedExceptions::parsePort)
                .flatMap(Optional::stream).toList();
        System.out.println("Optional + flatMap:               " + valid);

        // State-testing method, then the action
        for (String s : inputs) {
            if (isValidPort(s)) {
                System.out.println("connect to port " + port(s));
            }
        }
        try {
            port("http"); // calling the action without testing is a bug -> unchecked
        } catch (IllegalArgumentException e) {
            System.out.println("unchecked: " + e.getMessage());
        }
    }
}

/* Output:
checked, with try/catch in lambda: [8080, -, -, 443]
Optional + flatMap:               [8080, 443]
connect to port 8080
connect to port 443
unchecked: invalid port: http
*/
