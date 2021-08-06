// Effective Java, Item 11: Always override hashCode when you override equals.
//
// The hashCode contract says that equal objects must have equal hash codes.
// A class that overrides equals but not hashCode inherits Object's identity
// hash, so two logically equal instances land in different buckets and
// HashMap/HashSet cannot find them. A good hashCode combines every field used
// by equals (31 * result + field) and ignores fields equals ignores.
// Objects.hash is convenient but slower (varargs array, boxing); immutable
// classes may cache the value. Do not specify the exact algorithm in your
// documentation, so you keep the freedom to change it.
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class HashCodeDemo {
    public static void main(String[] args) {
        // Without hashCode: the map cannot find an equal key
        Map<NoHashPhone, String> broken = new HashMap<>();
        broken.put(new NoHashPhone(707, 867, 5309), "Jenny");
        NoHashPhone key = new NoHashPhone(707, 867, 5309);
        System.out.println("equal keys:         " + key.equals(new NoHashPhone(707, 867, 5309)));
        System.out.println("broken map lookup:  " + broken.get(key));

        // With hashCode: works as expected
        Map<PhoneNumber, String> map = new HashMap<>();
        map.put(new PhoneNumber(707, 867, 5309), "Jenny");
        System.out.println("correct map lookup: " + map.get(new PhoneNumber(707, 867, 5309)));

        // A legal but terrible hashCode: every object in one bucket
        Set<Integer> distinct = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            distinct.add(new ConstantHashPhone(1, 2, i).hashCode());
        }
        System.out.println("distinct hashes, constant hashCode: " + distinct.size());
        distinct.clear();
        for (int i = 0; i < 1000; i++) {
            distinct.add(new PhoneNumber(1, 2, i).hashCode());
        }
        System.out.println("distinct hashes, good hashCode:     " + distinct.size());

        // Objects.hash gives an equally valid (if slower) hash
        System.out.println("Objects.hash agrees on equal objects: "
                + (Objects.hash(707, 867, 5309) == Objects.hash(707, 867, 5309)));
    }
}

// Broken: equals without hashCode
final class NoHashPhone {
    private final short areaCode, prefix, lineNum;

    NoHashPhone(int areaCode, int prefix, int lineNum) {
        this.areaCode = (short) areaCode;
        this.prefix = (short) prefix;
        this.lineNum = (short) lineNum;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof NoHashPhone)) {
            return false;
        }
        NoHashPhone p = (NoHashPhone) o;
        return p.areaCode == areaCode && p.prefix == prefix && p.lineNum == lineNum;
    }
}

// Legal (equal objects share a hash) but turns a HashMap into a linked list
final class ConstantHashPhone {
    private final int areaCode, prefix, lineNum;

    ConstantHashPhone(int areaCode, int prefix, int lineNum) {
        this.areaCode = areaCode;
        this.prefix = prefix;
        this.lineNum = lineNum;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ConstantHashPhone && ((ConstantHashPhone) o).areaCode == areaCode
                && ((ConstantHashPhone) o).prefix == prefix && ((ConstantHashPhone) o).lineNum == lineNum;
    }

    @Override
    public int hashCode() {
        return 42; // never do this
    }
}

// Recommended
final class PhoneNumber {
    private final short areaCode, prefix, lineNum;
    private int hashCode; // lazily cached; 0 means "not computed yet"

    PhoneNumber(int areaCode, int prefix, int lineNum) {
        this.areaCode = (short) areaCode;
        this.prefix = (short) prefix;
        this.lineNum = (short) lineNum;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof PhoneNumber)) {
            return false;
        }
        PhoneNumber p = (PhoneNumber) o;
        return p.areaCode == areaCode && p.prefix == prefix && p.lineNum == lineNum;
    }

    @Override
    public int hashCode() {
        int result = hashCode;
        if (result == 0) {
            result = Short.hashCode(areaCode);
            result = 31 * result + Short.hashCode(prefix);
            result = 31 * result + Short.hashCode(lineNum);
            hashCode = result;
        }
        return result;
    }
}

/* Output:
equal keys:         true
broken map lookup:  null
correct map lookup: Jenny
distinct hashes, constant hashCode: 1
distinct hashes, good hashCode:     1000
Objects.hash agrees on equal objects: true
*/
