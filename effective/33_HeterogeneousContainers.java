// Effective Java, Item 33: Consider typesafe heterogeneous containers.
//
// Normally the container is parameterized (Set<E>, Map<K, V>), which fixes
// the number of type parameters. If you need a container that holds values
// of many different types, parameterize the KEY instead: use Class<T>
// objects as keys ("type tokens"). Store values as Object and recover the
// type with Class.cast, which is checked, so getFavorite(String.class) is
// guaranteed to return a String. Calling type.cast in put as well stops a
// client using raw types from corrupting the container. Annotation lookup
// (getAnnotation(Class<T>)) is the same idea in the JDK.
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class HeterogeneousContainers {
    @Retention(RetentionPolicy.RUNTIME)
    @interface Owner {
        String value();
    }

    @Owner("platform-team")
    static class Service {
    }

    public static void main(String[] args) {
        Favorites f = new Favorites();
        f.putFavorite(String.class, "Java");
        f.putFavorite(Integer.class, 0xcafebabe);
        f.putFavorite(Class.class, Favorites.class);

        // Each value comes back with its own static type, no casts
        String favoriteString = f.getFavorite(String.class);
        int favoriteInteger = f.getFavorite(Integer.class);
        Class<?> favoriteClass = f.getFavorite(Class.class);
        System.out.printf("%s %x %s%n", favoriteString, favoriteInteger, favoriteClass.getSimpleName());

        // A raw Class defeats the compile-time check, but the dynamic cast in
        // putFavorite still rejects the wrong value
        try {
            rawTypeAbuse(f);
        } catch (ClassCastException e) {
            System.out.println("raw-type abuse rejected: " + e.getMessage());
        }

        // The JDK's own typesafe heterogeneous container: annotations
        Owner owner = Service.class.getAnnotation(Owner.class);
        System.out.println("Service owner: " + owner.value());
    }

    @SuppressWarnings({"rawtypes", "unchecked"}) // deliberate misuse
    static void rawTypeAbuse(Favorites f) {
        Class rawInteger = Integer.class;
        f.putFavorite(rawInteger, "not an integer");
    }
}

class Favorites {
    private final Map<Class<?>, Object> favorites = new HashMap<>();

    <T> void putFavorite(Class<T> type, T instance) {
        favorites.put(Objects.requireNonNull(type), type.cast(instance));
    }

    <T> T getFavorite(Class<T> type) {
        return type.cast(favorites.get(type));
    }
}

/* Output:
Java cafebabe Favorites
raw-type abuse rejected: Cannot cast java.lang.String to java.lang.Integer
Service owner: platform-team
*/
