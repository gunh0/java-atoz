// LinkedList is a doubly linked list that implements both List and Deque.
//  - adding or removing at either END is O(1): addFirst/addLast, pollFirst/pollLast
//  - get(i) must walk node by node from the nearer end: O(n)
//  - inserting in the middle is O(1) only once you are there, e.g. with a ListIterator
// In practice ArrayList is faster for most List work and ArrayDeque for most
// queue work (better memory locality, no node per element). LinkedList still
// shows up as a List that is also a Deque, and it accepts null elements.
import java.util.LinkedList;
import java.util.ListIterator;

public class LinkedListExample {
    public static void main(String[] args) {
        LinkedList<String> playlist = new LinkedList<>();
        playlist.add("Intro");
        playlist.add("Track A");
        playlist.addLast("Outro");
        playlist.addFirst("Jingle");
        System.out.println("playlist: " + playlist);
        System.out.println("first=" + playlist.getFirst() + ", last=" + playlist.getLast());

        // Deque operations at both ends
        System.out.println("pollFirst(): " + playlist.pollFirst());
        System.out.println("pollLast():  " + playlist.pollLast());
        System.out.println("after polling: " + playlist);

        // Queue view: offer at the tail, poll from the head
        LinkedList<String> jobs = new LinkedList<>();
        jobs.offer("job-1");
        jobs.offer("job-2");
        jobs.offer("job-3");
        System.out.println("peek(): " + jobs.peek() + ", poll(): " + jobs.poll() + ", left: " + jobs);

        // Stack view: push/pop work on the head
        LinkedList<String> history = new LinkedList<>();
        history.push("/home");
        history.push("/docs");
        history.push("/docs/api");
        System.out.println("back from " + history.pop() + " to " + history.peek());

        // Insert in the middle while walking: no shifting, just relinking
        ListIterator<String> it = playlist.listIterator();
        while (it.hasNext()) {
            if (it.next().equals("Intro")) {
                it.add("Interlude"); // inserted right after "Intro"
            }
        }
        System.out.println("with interlude: " + playlist);

        // Empty-list behaviour: get/remove throw, peek/poll return null
        LinkedList<String> empty = new LinkedList<>();
        System.out.println("empty.peekFirst(): " + empty.peekFirst());
        try {
            empty.getFirst();
        } catch (java.util.NoSuchElementException e) {
            System.out.println("empty.getFirst(): NoSuchElementException");
        }

        // Unlike ArrayDeque, LinkedList allows null
        empty.add(null);
        System.out.println("null element allowed, size=" + empty.size());
    }
}

/* Output:
playlist: [Jingle, Intro, Track A, Outro]
first=Jingle, last=Outro
pollFirst(): Jingle
pollLast():  Outro
after polling: [Intro, Track A]
peek(): job-1, poll(): job-1, left: [job-2, job-3]
back from /docs/api to /docs
with interlude: [Intro, Interlude, Track A]
empty.peekFirst(): null
empty.getFirst(): NoSuchElementException
null element allowed, size=1
*/
