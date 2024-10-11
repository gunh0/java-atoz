// OWASP Top 10 2021: A08:2021 Software and Data Integrity Failures, mass assignment (CWE-915)
// https://owasp.org/Top10/A08_2021-Software_and_Data_Integrity_Failures/
//
// Frameworks that bind request parameters (or JSON properties) straight
// onto a domain object by name are convenient: displayName=Bob sets
// user.displayName. They also set every other field the client thinks of:
// role=admin, emailVerified=true, balance=1000000, id=1. The server meant
// to let users edit two fields and silently let them edit all of them.
// Bind requests to a dedicated input type (a record) that contains only
// the editable fields, reject unknown parameters, and copy the values onto
// the domain object explicitly. This program uses a tiny reflective binder
// in place of a framework.
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class A08_IntegrityFailures_MassAssignment {
    public static void main(String[] args) throws Exception {
        // PATCH /me  displayName=Bob&email=bob@example.com&role=admin&emailVerified=true
        Map<String, String> request = new LinkedHashMap<>();
        request.put("displayName", "Bob");
        request.put("email", "bob@example.com");
        request.put("role", "admin");
        request.put("emailVerified", "true");

        // Noncompliant: bind every parameter onto the entity
        User bob = new User(42, "bob", "bob@old.example", "user", false);
        bindAllFields(bob, request);
        System.out.println("noncompliant: " + bob);

        // Compliant: bind to a DTO with only editable fields, reject anything else
        User bob2 = new User(42, "bob", "bob@old.example", "user", false);
        try {
            ProfileUpdate update = bindRecord(ProfileUpdate.class, request);
            update.applyTo(bob2);
        } catch (IllegalArgumentException e) {
            System.out.println("compliant:    request rejected: " + e.getMessage());
        }
        Map<String, String> honest = Map.of("displayName", "Bob", "email", "bob@example.com");
        bindRecord(ProfileUpdate.class, honest).applyTo(bob2);
        System.out.println("compliant:    " + bob2);
    }

    // A naive binder: any parameter that names a field sets that field
    static void bindAllFields(Object target, Map<String, String> params) throws IllegalAccessException {
        for (Map.Entry<String, String> p : params.entrySet()) {
            try {
                Field f = target.getClass().getDeclaredField(p.getKey());
                f.setAccessible(true);
                f.set(target, convert(f.getType(), p.getValue()));
            } catch (NoSuchFieldException ignored) {
                // unknown names are skipped
            }
        }
    }

    // A strict binder: only the record's components, nothing unknown
    static <R extends Record> R bindRecord(Class<R> type, Map<String, String> params) throws Exception {
        RecordComponent[] components = type.getRecordComponents();
        Set<String> allowed = new TreeSet<>();
        for (RecordComponent c : components) allowed.add(c.getName());
        Set<String> unknown = new TreeSet<>(params.keySet());
        unknown.removeAll(allowed);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("unexpected fields " + unknown);
        }
        Object[] values = new Object[components.length];
        Class<?>[] types = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            values[i] = convert(types[i], params.get(components[i].getName()));
        }
        return type.getDeclaredConstructor(types).newInstance(values);
    }

    static Object convert(Class<?> type, String value) {
        if (value == null) return null;
        if (type == boolean.class) return Boolean.parseBoolean(value);
        if (type == long.class) return Long.parseLong(value);
        return value;
    }
}

class User {
    private final long id;
    private String displayName;
    private String email;
    private String role;
    private boolean emailVerified;

    User(long id, String displayName, String email, String role, boolean emailVerified) {
        this.id = id;
        this.displayName = displayName;
        this.email = email;
        this.role = role;
        this.emailVerified = emailVerified;
    }

    void updateProfile(String displayName, String email) {
        this.displayName = displayName;
        if (!email.equals(this.email)) {
            this.email = email;
            this.emailVerified = false; // a changed address must be verified again
        }
    }

    @Override
    public String toString() {
        return "User[id=" + id + ", displayName=" + displayName + ", email=" + email
                + ", role=" + role + ", emailVerified=" + emailVerified + "]";
    }
}

// The input type: exactly what a user may change about themselves
record ProfileUpdate(String displayName, String email) {
    ProfileUpdate {
        if (displayName == null || displayName.isBlank() || displayName.length() > 40) {
            throw new IllegalArgumentException("displayName");
        }
        if (email == null || !email.matches("[^@\\s]+@[^@\\s]+")) {
            throw new IllegalArgumentException("email");
        }
    }

    void applyTo(User user) {
        user.updateProfile(displayName, email);
    }
}

/* Output:
noncompliant: User[id=42, displayName=Bob, email=bob@example.com, role=admin, emailVerified=true]
compliant:    request rejected: unexpected fields [emailVerified, role]
compliant:    User[id=42, displayName=Bob, email=bob@example.com, role=user, emailVerified=false]
*/
