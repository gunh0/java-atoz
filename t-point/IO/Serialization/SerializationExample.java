// Java serialization turns an object graph into bytes (ObjectOutputStream)
// and back (ObjectInputStream). A class opts in by implementing Serializable.
//  - transient fields are skipped and come back as 0 / false / null
//  - serialVersionUID identifies the class version; declare it explicitly,
//    or a recompiled class may get a different computed one
//    (InvalidClassException on read)
//  - deserialization does NOT run constructors, so a private readObject that
//    re-checks invariants is the only place to validate incoming data
//  - shared references stay shared after a round trip
// Never deserialize untrusted bytes; prefer JSON or similar for data exchange.
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SerializationExample {
    static final class Account implements Serializable {
        @Serial private static final long serialVersionUID = 1L;

        private final String username;
        private int failedLogins;
        private transient String sessionToken; // never written to the stream

        Account(String username, int failedLogins, String sessionToken) {
            if (failedLogins < 0) throw new IllegalArgumentException("failedLogins < 0");
            this.username = username;
            this.failedLogins = failedLogins;
            this.sessionToken = sessionToken;
        }

        // Called instead of a constructor during deserialization
        @Serial
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            if (failedLogins < 0) throw new InvalidObjectException("failedLogins < 0");
        }

        @Override
        public String toString() {
            return "Account[" + username + ", failedLogins=" + failedLogins + ", sessionToken=" + sessionToken + "]";
        }
    }

    static final class Team implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        final List<Account> members = new ArrayList<>();
        Account lead;
    }

    static byte[] serialize(Object o) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(o);
        }
        return bytes.toByteArray();
    }

    static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return in.readObject();
        }
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("ser");
        Path file = dir.resolve("account.ser");
        try {
            // Round trip through a file: the transient token is gone
            Account alice = new Account("alice", 2, "s3cr3t-token");
            Files.write(file, serialize(alice));
            Account restored = (Account) deserialize(Files.readAllBytes(file));
            System.out.println("before: " + alice);
            System.out.println("after:  " + restored);
            System.out.println("same object? " + (alice == restored));

            // Shared references survive
            Team team = new Team();
            team.members.add(alice);
            team.lead = alice;
            Team copy = (Team) deserialize(serialize(team));
            System.out.println("lead is the first member after round trip? " + (copy.lead == copy.members.get(0)));

            // Tampered bytes: flip failedLogins (the int after the username) to -1
            byte[] data = serialize(new Account("bob", 7, null));
            int idx = indexOfInt(data, 7);
            data[idx] = data[idx + 1] = data[idx + 2] = data[idx + 3] = (byte) 0xFF;
            try {
                deserialize(data);
            } catch (InvalidObjectException e) {
                System.out.println("tampered stream rejected: " + e.getMessage());
            }

            // Non-serializable fields fail at write time
            try {
                serialize(new Object[] {new Object()});
            } catch (java.io.NotSerializableException e) {
                System.out.println("NotSerializableException: " + e.getMessage());
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }

    // Finds the 4 big-endian bytes of an int value in the serialized data
    static int indexOfInt(byte[] data, int value) {
        for (int i = data.length - 4; i >= 0; i--) {
            if (data[i] == 0 && data[i + 1] == 0 && data[i + 2] == 0 && data[i + 3] == value) return i;
        }
        throw new IllegalStateException("not found");
    }
}

/* Output:
before: Account[alice, failedLogins=2, sessionToken=s3cr3t-token]
after:  Account[alice, failedLogins=2, sessionToken=null]
same object? false
lead is the first member after round trip? true
tampered stream rejected: failedLogins < 0
NotSerializableException: java.lang.Object
*/
