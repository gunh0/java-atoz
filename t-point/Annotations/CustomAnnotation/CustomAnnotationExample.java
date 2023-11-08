// An annotation is metadata; it does nothing until some code reads it.
// To read one at run time with reflection it needs:
//   @Retention(RetentionPolicy.RUNTIME)  kept in the class file AND loaded
//                                        (the default, CLASS, is invisible to reflection)
//   @Target(ElementType.FIELD)           where it may be placed
// Annotation elements look like methods and may have defaults; allowed types
// are primitives, String, Class, enums, other annotations and arrays of those.
// This file builds a small validator (@NotBlank, @Range) and a log-safe
// toString that masks fields marked @Sensitive, all driven by reflection.
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class CustomAnnotationExample {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface NotBlank {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Range {
        int min() default 0;
        int max() default Integer.MAX_VALUE;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Sensitive {
        int visibleSuffix() default 0; // how many trailing characters to keep
    }

    // Default retention (CLASS): present in the bytecode, absent at run time
    @Target(ElementType.FIELD)
    @interface Internal {}

    static class ApiClient {
        @NotBlank String name;
        @Range(min = 1, max = 65535) int port;
        @Range(min = 1, max = 300) int timeoutSeconds;
        @Sensitive(visibleSuffix = 4) @NotBlank String apiKey;
        @Sensitive String password;
        @Internal String note;

        ApiClient(String name, int port, int timeoutSeconds, String apiKey, String password) {
            this.name = name;
            this.port = port;
            this.timeoutSeconds = timeoutSeconds;
            this.apiKey = apiKey;
            this.password = password;
            this.note = "n/a";
        }
    }

    static List<String> validate(Object target) throws IllegalAccessException {
        List<String> errors = new ArrayList<>();
        for (Field f : sortedFields(target.getClass())) {
            f.setAccessible(true);
            Object value = f.get(target);
            if (f.isAnnotationPresent(NotBlank.class) && (value == null || value.toString().isBlank())) {
                errors.add(f.getName() + " must not be blank");
            }
            Range range = f.getAnnotation(Range.class);
            if (range != null) {
                int n = (Integer) value;
                if (n < range.min() || n > range.max()) {
                    errors.add(f.getName() + "=" + n + " not in [" + range.min() + ", " + range.max() + "]");
                }
            }
        }
        return errors;
    }

    static String safeToString(Object target) throws IllegalAccessException {
        StringJoiner out = new StringJoiner(", ", target.getClass().getSimpleName() + "[", "]");
        for (Field f : sortedFields(target.getClass())) {
            f.setAccessible(true);
            Object value = f.get(target);
            Sensitive s = f.getAnnotation(Sensitive.class);
            if (s != null && value != null) {
                String text = value.toString();
                int keep = Math.min(s.visibleSuffix(), text.length());
                value = "*".repeat(8) + text.substring(text.length() - keep);
            }
            out.add(f.getName() + "=" + value);
        }
        return out.toString();
    }

    // Reflection does not promise field order; sort by name for stable output
    static List<Field> sortedFields(Class<?> type) {
        List<Field> fields = new ArrayList<>(List.of(type.getDeclaredFields()));
        fields.sort(java.util.Comparator.comparing(Field::getName));
        return fields;
    }

    public static void main(String[] args) throws Exception {
        ApiClient good = new ApiClient("billing", 443, 30, "sk_live_9f8e7d6c5b4a", "hunter2");
        ApiClient bad = new ApiClient(" ", 70000, 0, "", "secret");

        System.out.println("good: " + safeToString(good));
        System.out.println("  errors: " + validate(good));
        System.out.println("bad:  " + safeToString(bad));
        validate(bad).forEach(e -> System.out.println("  error: " + e));

        // Reading annotations and their element values directly
        Field apiKey = ApiClient.class.getDeclaredField("apiKey");
        System.out.println("apiKey annotations: " + apiKey.getDeclaredAnnotations().length
                + ", visibleSuffix=" + apiKey.getAnnotation(Sensitive.class).visibleSuffix());
        Field port = ApiClient.class.getDeclaredField("port");
        Range r = port.getAnnotation(Range.class);
        System.out.println("port @Range(min=" + r.min() + ", max=" + r.max() + "), annotationType=" + r.annotationType().getSimpleName());
        Field note = ApiClient.class.getDeclaredField("note");
        System.out.println("@Internal (CLASS retention) visible at run time: " + note.isAnnotationPresent(Internal.class));
    }
}

/* Output:
good: ApiClient[apiKey=********5b4a, name=billing, note=n/a, password=********, port=443, timeoutSeconds=30]
  errors: []
bad:  ApiClient[apiKey=********, name= , note=n/a, password=********, port=70000, timeoutSeconds=0]
  error: apiKey must not be blank
  error: name must not be blank
  error: port=70000 not in [1, 65535]
  error: timeoutSeconds=0 not in [1, 300]
apiKey annotations: 2, visibleSuffix=4
port @Range(min=1, max=65535), annotationType=Range
@Internal (CLASS retention) visible at run time: false
*/
