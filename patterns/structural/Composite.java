// Composite (structural): compose objects into tree structures and let
// clients treat individual objects and groups of objects uniformly.
//
// A leaf and a container implement the same interface; the container
// forwards operations to its children and combines the results. Here the
// tree is a cloud resource hierarchy (organization > folders > projects),
// and "monthly cost" and "find public buckets" work the same on a single
// project or on a whole folder, without the caller checking types.
// JDK examples: java.awt.Container is itself a Component, and in javax.swing
// every JComponent can hold other components.
import java.util.ArrayList;
import java.util.List;

public class CompositeDemo {
    // The component interface shared by leaves and composites
    interface Resource {
        String name();
        int monthlyCost();
        List<String> publicBuckets();
        void print(String indent);
    }

    // Leaf
    record Project(String name, int monthlyCost, List<String> publicBuckets) implements Resource {
        public void print(String indent) {
            System.out.println(indent + "- " + name + " ($" + monthlyCost + ")");
        }
    }

    // Composite: holds children of the component type, so folders can nest
    static final class Folder implements Resource {
        private final String name;
        private final List<Resource> children = new ArrayList<>();

        Folder(String name) { this.name = name; }

        Folder add(Resource child) { children.add(child); return this; }

        public String name() { return name; }

        public int monthlyCost() {
            int sum = 0;
            for (Resource child : children) sum += child.monthlyCost(); // recursion via the interface
            return sum;
        }

        public List<String> publicBuckets() {
            List<String> all = new ArrayList<>();
            for (Resource child : children) {
                for (String bucket : child.publicBuckets()) all.add(child.name() + "/" + bucket);
            }
            return all;
        }

        public void print(String indent) {
            System.out.println(indent + "+ " + name + " ($" + monthlyCost() + ")");
            for (Resource child : children) child.print(indent + "  ");
        }
    }

    public static void main(String[] args) {
        Folder org = new Folder("example-org")
                .add(new Folder("production")
                        .add(new Project("payments", 4200, List.of()))
                        .add(new Project("web-frontend", 1300, List.of("static-assets"))))
                .add(new Folder("sandbox")
                        .add(new Project("ml-experiments", 2750, List.of("datasets-export")))
                        .add(new Folder("interns")
                                .add(new Project("intern-2022", 90, List.of("tmp-share")))))
                .add(new Project("shared-logging", 610, List.of()));

        org.print("");

        // Same calls on a leaf, a sub-tree and the root
        List<Resource> scopes = List.of(new Project("payments", 4200, List.of()), org, new Folder("empty"));
        for (Resource scope : scopes) {
            System.out.println(scope.name() + ": cost $" + scope.monthlyCost()
                    + ", public buckets " + scope.publicBuckets());
        }
    }
}

/* Output:
+ example-org ($8950)
  + production ($5500)
    - payments ($4200)
    - web-frontend ($1300)
  + sandbox ($2840)
    - ml-experiments ($2750)
    + interns ($90)
      - intern-2022 ($90)
  - shared-logging ($610)
payments: cost $4200, public buckets []
example-org: cost $8950, public buckets [production/web-frontend/static-assets, sandbox/ml-experiments/datasets-export, sandbox/interns/intern-2022/tmp-share]
empty: cost $0, public buckets []
*/
