// Meta-annotations are annotations on annotation types; they decide where an
// annotation may appear and how long it lives:
//   @Retention  SOURCE (dropped by javac), CLASS (in the .class file but not
//               visible to reflection, the default) or RUNTIME (reflectable)
//   @Target     which declarations it may annotate (TYPE, METHOD, FIELD, ...)
//   @Inherited  a class annotation is also reported for subclasses
//   @Repeatable the annotation may appear several times on one element; javac
//               wraps the copies in a container annotation
// getAnnotationsByType() sees through the container, getAnnotation() does not.
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class RepeatableAnnotationExample {
    // A repeatable annotation and its container
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Repeatable(RequiresRoles.class)
    @interface RequiresRole {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface RequiresRoles {
        RequiresRole[] value();
    }

    // Inherited class-level annotation
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    @interface Audited {}

    // CLASS retention: compiled into the class file, invisible at runtime
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.METHOD)
    @interface Internal {}

    @Audited
    static class BaseController {}

    static class AdminController extends BaseController {
        @RequiresRole("admin")
        public void deleteUser() {}

        @RequiresRole("admin")
        @RequiresRole("security")
        public void rotateKeys() {}

        @Internal
        public void health() {}
    }

    // A tiny authorization check driven by the annotations
    static boolean allowed(Method m, Set<String> userRoles) {
        RequiresRole[] required = m.getAnnotationsByType(RequiresRole.class);
        return Arrays.stream(required).allMatch(r -> userRoles.contains(r.value()));
    }

    public static void main(String[] args) throws Exception {
        Method[] methods = AdminController.class.getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName)); // reflection order is unspecified

        for (Method m : methods) {
            String roles = Arrays.stream(m.getAnnotationsByType(RequiresRole.class))
                    .map(RequiresRole::value).collect(Collectors.joining(", ", "[", "]"));
            System.out.printf("%-10s getAnnotationsByType=%-19s getAnnotation(RequiresRole)=%s, container present=%s%n",
                    m.getName(), roles,
                    m.getAnnotation(RequiresRole.class) != null ? "found" : "null",
                    m.isAnnotationPresent(RequiresRoles.class));
        }

        Method health = AdminController.class.getMethod("health");
        System.out.println("@Internal (CLASS retention) visible at runtime: " + (health.getAnnotations().length > 0));
        System.out.println("@Audited inherited by AdminController: " + AdminController.class.isAnnotationPresent(Audited.class));
        System.out.println("declared directly on AdminController: " + (AdminController.class.getDeclaredAnnotation(Audited.class) != null));

        Set<String> alice = Set.of("admin");
        Set<String> bob = Set.of("admin", "security");
        Method rotate = AdminController.class.getMethod("rotateKeys");
        System.out.println("alice " + alice + " may rotateKeys: " + allowed(rotate, alice));
        System.out.println("bob " + new TreeSet<>(bob) + " may rotateKeys: " + allowed(rotate, bob));
    }
}

/* Output:
deleteUser getAnnotationsByType=[admin]             getAnnotation(RequiresRole)=found, container present=false
health     getAnnotationsByType=[]                  getAnnotation(RequiresRole)=null, container present=false
rotateKeys getAnnotationsByType=[admin, security]   getAnnotation(RequiresRole)=null, container present=true
@Internal (CLASS retention) visible at runtime: false
@Audited inherited by AdminController: true
declared directly on AdminController: false
alice [admin] may rotateKeys: false
bob [admin, security] may rotateKeys: true
*/
