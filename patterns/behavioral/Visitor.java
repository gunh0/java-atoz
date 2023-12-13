// Visitor (behavioral): represent an operation to be performed on the
// elements of an object structure. Visitor lets you define a new operation
// without changing the classes of the elements on which it operates.
//
// Here an infrastructure-as-code plan holds three kinds of resources. New
// operations over them (a security linter, a cost estimate) are visitors.
// The classic form needs double dispatch: element.accept(visitor) picks the
// element's type, then visitor.visit(this) picks the overload, because Java
// overloads are resolved at compile time.
// With sealed interfaces and pattern-matching switch (both final in Java 21)
// the same "operation outside the hierarchy" needs no accept() methods: the
// switch dispatches on the runtime type and the compiler checks that every
// permitted subtype is covered, so adding a resource type breaks the build
// exactly where an operation must be updated, just like adding a visit method.
// JDK examples: java.nio.file.FileVisitor (Files.walkFileTree) and
// javax.lang.model.element.ElementVisitor in annotation processing.
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class VisitorDemo {
    // ---- Classic visitor: elements accept, visitors visit ----

    interface ResourceVisitor<R> {
        R visitBucket(Bucket b);
        R visitSecurityGroup(SecurityGroup g);
        R visitVm(Vm vm);
    }

    sealed interface Resource permits Bucket, SecurityGroup, Vm {
        String name();
        <R> R accept(ResourceVisitor<R> v);
    }

    record Bucket(String name, boolean publicRead, int gigabytes) implements Resource {
        public <R> R accept(ResourceVisitor<R> v) { return v.visitBucket(this); }
    }

    record SecurityGroup(String name, Set<Integer> openToInternet) implements Resource {
        public <R> R accept(ResourceVisitor<R> v) { return v.visitSecurityGroup(this); }
    }

    record Vm(String name, String size, boolean diskEncrypted) implements Resource {
        public <R> R accept(ResourceVisitor<R> v) { return v.visitVm(this); }
    }

    // Operation 1: security findings
    static final class SecurityLinter implements ResourceVisitor<List<String>> {
        public List<String> visitBucket(Bucket b) {
            return b.publicRead() ? List.of(b.name() + ": bucket is publicly readable") : List.of();
        }

        public List<String> visitSecurityGroup(SecurityGroup g) {
            List<String> out = new ArrayList<>();
            for (int port : List.of(22, 3389)) {
                if (g.openToInternet().contains(port)) out.add(g.name() + ": port " + port + " open to 0.0.0.0/0");
            }
            return out;
        }

        public List<String> visitVm(Vm vm) {
            return vm.diskEncrypted() ? List.of() : List.of(vm.name() + ": disk not encrypted");
        }
    }

    // Operation 2: monthly cost, added without touching the resource records
    static final class CostEstimator implements ResourceVisitor<Integer> {
        public Integer visitBucket(Bucket b) { return b.gigabytes() / 40; }
        public Integer visitSecurityGroup(SecurityGroup g) { return 0; }
        public Integer visitVm(Vm vm) { return vm.size().equals("large") ? 140 : 35; }
    }

    // ---- The same operation with a sealed hierarchy and pattern matching ----

    static List<String> lint(Resource r) {
        return switch (r) {
            case Bucket b when b.publicRead() -> List.of(b.name() + ": bucket is publicly readable");
            case Bucket b -> List.of();
            case SecurityGroup(String name, Set<Integer> open) -> open.stream()
                    .filter(p -> p == 22 || p == 3389).sorted()
                    .map(p -> name + ": port " + p + " open to 0.0.0.0/0").toList();
            case Vm(String name, String size, boolean encrypted) ->
                    encrypted ? List.of() : List.of(name + ": disk not encrypted");
            // no default: the compiler knows Resource permits exactly these three
        };
    }

    public static void main(String[] args) {
        List<Resource> plan = List.of(
                new Bucket("logs", false, 800),
                new Bucket("public-assets", true, 120),
                new SecurityGroup("bastion-sg", Set.of(22, 443)),
                new SecurityGroup("web-sg", Set.of(80, 443)),
                new Vm("db-1", "large", false),
                new Vm("web-1", "small", true));

        System.out.println("classic visitor, security linter:");
        SecurityLinter linter = new SecurityLinter();
        List<String> classic = new ArrayList<>();
        for (Resource r : plan) classic.addAll(r.accept(linter));
        classic.forEach(f -> System.out.println("  " + f));

        CostEstimator cost = new CostEstimator();
        int total = 0;
        for (Resource r : plan) total += r.accept(cost);
        System.out.println("classic visitor, estimated cost: $" + total + "/month");

        System.out.println("sealed interface + pattern-matching switch:");
        List<String> modern = plan.stream().flatMap(r -> lint(r).stream()).toList();
        modern.forEach(f -> System.out.println("  " + f));
        System.out.println("same findings: " + classic.equals(modern));
    }
}

/* Output:
classic visitor, security linter:
  public-assets: bucket is publicly readable
  bastion-sg: port 22 open to 0.0.0.0/0
  db-1: disk not encrypted
classic visitor, estimated cost: $198/month
sealed interface + pattern-matching switch:
  public-assets: bucket is publicly readable
  bastion-sg: port 22 open to 0.0.0.0/0
  db-1: disk not encrypted
same findings: true
*/
