// Effective Java, Item 24: Favor static member classes over nonstatic.
//
// Java has four kinds of nested classes: static member, nonstatic member
// ("inner"), anonymous and local. An inner class instance is tied to an
// enclosing instance: it needs one to be created (outer.new Inner()) and
// keeps a hidden reference to it, which costs space and can keep a large
// outer object alive long after it is otherwise garbage. So if a member
// class does not need its enclosing instance, declare it static. Use inner
// classes for things like iterators or views that really operate on the
// outer object. (Since Java 18, javac drops the hidden field when an inner
// class never uses it, but the language-level coupling stays.)
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class StaticMemberClasses {
    public static void main(String[] args) throws ReflectiveOperationException {
        Playlist playlist = new Playlist("intro", "verse", "chorus");

        // Static member class: created without any Playlist instance
        Playlist.Track track = new Playlist.Track("bonus", 215);
        System.out.println("static member: " + track + ", fields " + fieldNames(Playlist.Track.class));

        // Inner class: needs and remembers its enclosing instance
        Iterator<String> it = playlist.iterator();
        StringBuilder sb = new StringBuilder();
        while (it.hasNext()) {
            sb.append(it.next()).append(' ');
        }
        System.out.println("inner iterator: " + sb.toString().trim());
        System.out.println("inner fields: " + fieldNames(it.getClass()));
        Field outer = it.getClass().getDeclaredField("this$0");
        outer.setAccessible(true);
        System.out.println("iterator keeps the playlist reachable: " + (outer.get(it) == playlist));

        // Creating an inner instance explicitly needs an outer instance
        Playlist.Cursor cursor = playlist.new Cursor();
        System.out.println("explicit outer.new Inner(): " + cursor.current());

        // Anonymous class for a one-off function object (a lambda is shorter)
        Comparable<String> anon = new Comparable<>() {
            @Override
            public int compareTo(String o) {
                return o.length();
            }
        };
        System.out.println("anonymous class compareTo(\"abc\"): " + anon.compareTo("abc"));
    }

    static String fieldNames(Class<?> c) {
        return Arrays.toString(Arrays.stream(c.getDeclaredFields()).map(Field::getName).sorted().toArray());
    }
}

class Playlist implements Iterable<String> {
    private final String[] songs;

    Playlist(String... songs) {
        this.songs = songs.clone();
    }

    // Does not use Playlist's state: static
    static final class Track {
        private final String title;
        private final int seconds;

        Track(String title, int seconds) {
            this.title = title;
            this.seconds = seconds;
        }

        @Override
        public String toString() {
            return title + " (" + seconds / 60 + ":" + String.format("%02d", seconds % 60) + ")";
        }
    }

    // Operates on the enclosing playlist: inner class is the right choice
    private class SongIterator implements Iterator<String> {
        private int next = 0;

        @Override
        public boolean hasNext() {
            return next < songs.length;
        }

        @Override
        public String next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return songs[next++];
        }
    }

    class Cursor {
        String current() {
            return songs[0];
        }
    }

    @Override
    public Iterator<String> iterator() {
        return new SongIterator();
    }
}

/* Output:
static member: bonus (3:35), fields [seconds, title]
inner iterator: intro verse chorus
inner fields: [next, this$0]
iterator keeps the playlist reachable: true
explicit outer.new Inner(): intro
anonymous class compareTo("abc"): 3
*/
