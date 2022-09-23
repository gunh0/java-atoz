// A class declared inside another class is a nested class. Two main kinds:
//  - static nested class: just a class scoped inside its outer class. It has
//    no link to an outer instance; create it with new Outer.Nested().
//  - inner class (non-static member class): every instance is tied to an
//    instance of the outer class and can read its fields, even private ones.
//    Create it with outerObject.new Inner(), and refer to the outer object
//    explicitly with Outer.this.
// Prefer static nested classes unless you really need the outer instance: an
// inner object keeps its outer object alive.
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class InnerClassExample {
    public static void main(String[] args) {
        Playlist playlist = new Playlist("Focus");
        playlist.add(new Playlist.Track("Intro", 95));      // static nested
        playlist.add(new Playlist.Track("Deep Work", 241));
        playlist.add(new Playlist.Track("Outro", 130));

        // The inner iterator reads the playlist's private list directly
        for (Playlist.Track t : playlist) {
            System.out.println(t);
        }

        // Creating an inner instance needs an outer instance
        Playlist.Cursor cursor = playlist.new Cursor();
        cursor.next();
        cursor.next();
        System.out.println(cursor.where());

        System.out.println("total seconds: " + playlist.totalSeconds());
    }
}

class Playlist implements Iterable<Playlist.Track> {
    private final String name;
    private final List<Track> tracks = new ArrayList<>();

    Playlist(String name) {
        this.name = name;
    }

    void add(Track track) {
        tracks.add(track);
    }

    int totalSeconds() {
        int sum = 0;
        for (Track t : tracks) {
            sum += t.seconds;          // the outer class may read the nested class's private field
        }
        return sum;
    }

    @Override
    public Iterator<Track> iterator() {
        return new Cursor();           // inside an instance method, `this` is the outer object
    }

    // Static nested: needs nothing from a Playlist instance
    static class Track {
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

    // Inner: each Cursor belongs to one Playlist
    class Cursor implements Iterator<Track> {
        private int position;

        @Override
        public boolean hasNext() {
            return position < tracks.size();   // outer's private field
        }

        @Override
        public Track next() {
            return tracks.get(position++);
        }

        String where() {
            // Playlist.this is the outer instance this cursor is bound to
            return "cursor at " + position + " of " + Playlist.this.tracks.size()
                    + " in '" + Playlist.this.name + "'";
        }
    }
}

/* Output:
Intro (1:35)
Deep Work (4:01)
Outro (2:10)
cursor at 2 of 3 in 'Focus'
total seconds: 466
*/
