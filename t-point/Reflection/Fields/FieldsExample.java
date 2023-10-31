// java.lang.reflect.Field describes one field of a class:
//  - getDeclaredFields(): every field declared in THIS class, any access level
//  - getFields(): public fields only, including inherited ones
//  - getType() / getGenericType(), getModifiers()
//  - get(obj) / set(obj, value); pass null as obj for static fields
// Private fields need setAccessible(true) first. That only works for your own
// code: since Java 16 the JDK's internals are strongly encapsulated, and
// setAccessible on, say, String's private fields throws
// InaccessibleObjectException. static final fields can never be set.
// The order of getDeclaredFields() is unspecified, so sort before printing.
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class FieldsExample {
    public static void main(String[] args) throws Exception {
        System.out.println("declared fields of Account:");
        Field[] fields = Account.class.getDeclaredFields();
        Arrays.sort(fields, Comparator.comparing(Field::getName));
        for (Field f : fields) {
            System.out.printf("  %-18s %-24s %s%n", f.getName(), f.getGenericType().getTypeName().replace("java.lang.", "").replace("java.util.", ""),
                    Modifier.toString(f.getModifiers()));
        }

        List<String> publicNames = Arrays.stream(AdminAccount.class.getFields()).map(Field::getName).sorted().toList();
        System.out.println("public fields of AdminAccount (incl. inherited): " + publicNames);

        AdminAccount admin = new AdminAccount("root");

        // Public field: no special access needed
        Field owner = Account.class.getField("owner");
        System.out.println("owner = " + owner.get(admin));

        // Private field: get() fails until setAccessible(true)
        Field hash = Account.class.getDeclaredField("passwordHash");
        try {
            hash.get(admin);
        } catch (IllegalAccessException e) {
            System.out.println("get private field: IllegalAccessException");
        }
        hash.setAccessible(true);
        System.out.println("passwordHash (after setAccessible) = " + hash.get(admin));
        hash.set(admin, "$2y$10$changed"); // reflection bypasses encapsulation entirely
        System.out.println("passwordHash after set = " + hash.get(admin));

        // Primitive fields have typed accessors
        Field failed = Account.class.getDeclaredField("failedLogins");
        failed.setInt(admin, 3);
        System.out.println("failedLogins = " + failed.getInt(admin));

        // Static fields: the target object is null
        Field realm = Account.class.getField("realm");
        realm.set(null, "prod");
        System.out.println("static realm = " + Account.realm);
        Field max = Account.class.getDeclaredField("MAX_FAILED_LOGINS");
        max.setAccessible(true);
        try {
            max.setInt(null, 1_000);
        } catch (IllegalAccessException e) {
            System.out.println("set static final field: IllegalAccessException");
        }

        // Strong encapsulation of the JDK
        Field value = String.class.getDeclaredField("value");
        try {
            value.setAccessible(true);
        } catch (InaccessibleObjectException e) {
            System.out.println("setAccessible on String.value: InaccessibleObjectException");
        }
        System.out.println("trySetAccessible on String.value: " + value.trySetAccessible());

        try {
            Account.class.getDeclaredField("password");
        } catch (NoSuchFieldException e) {
            System.out.println("getDeclaredField(\"password\"): NoSuchFieldException");
        }
    }
}

// Top-level (not nested) so that FieldsExample is not a nestmate with private access
class Account {
    public String owner;
    protected int failedLogins;
    private String passwordHash;
    private List<String> roles;
    static final int MAX_FAILED_LOGINS = 5;
    public static String realm = "corp";

    Account(String owner, String passwordHash, List<String> roles) {
        this.owner = owner;
        this.passwordHash = passwordHash;
        this.roles = roles;
    }
}

class AdminAccount extends Account {
    public boolean mfaRequired = true;

    AdminAccount(String owner) {
        super(owner, "$2y$10$abc", List.of("admin"));
    }
}

/* Output:
declared fields of Account:
  MAX_FAILED_LOGINS  int                      static final
  failedLogins       int                      protected
  owner              String                   public
  passwordHash       String                   private
  realm              String                   public static
  roles              List<String>             private
public fields of AdminAccount (incl. inherited): [mfaRequired, owner, realm]
owner = root
get private field: IllegalAccessException
passwordHash (after setAccessible) = $2y$10$abc
passwordHash after set = $2y$10$changed
failedLogins = 3
static realm = prod
set static final field: IllegalAccessException
setAccessible on String.value: InaccessibleObjectException
trySetAccessible on String.value: false
getDeclaredField("password"): NoSuchFieldException
*/
