// FIO05-J. Do not expose buffers or their backing arrays methods to
// untrusted code
// https://wiki.sei.cmu.edu/confluence/display/java/FIO05-J.+Do+not+expose+buffers+or+their+backing+arrays+methods+to+untrusted+code
//
// java.nio buffers are views. CharBuffer.wrap(array) does not copy the
// array; it wraps it, so anyone holding the buffer can put() into the
// class's private array. duplicate() and slice() create new buffer OBJECTS
// that still share the same content. Returning such a buffer to untrusted
// code is like returning the private array itself. Return
// asReadOnlyBuffer() (writes throw ReadOnlyBufferException and array() is
// unavailable), or a buffer over a fresh copy of the data.
import java.nio.CharBuffer;
import java.nio.ReadOnlyBufferException;

public class FIO05_ExposeBuffers {
    public static void main(String[] args) {
        // Noncompliant: wrap() shares the private array
        PasswordHolderNoncompliant bad = new PasswordHolderNoncompliant("hunter2".toCharArray());
        CharBuffer leaked = bad.getBuffer();
        leaked.put(0, 'X');
        System.out.println("noncompliant wrap():      internal now " + bad.peek());

        // Noncompliant: duplicate() is a new object over the same content
        CharBuffer dup = bad.getDuplicate();
        dup.put(1, 'Y');
        System.out.println("noncompliant duplicate(): internal now " + bad.peek()
                + ", hasArray = " + dup.hasArray());

        // Compliant: read-only view
        PasswordHolderCompliant good = new PasswordHolderCompliant("hunter2".toCharArray());
        CharBuffer view = good.getReadOnlyBuffer();
        try {
            view.put(0, 'X');
        } catch (ReadOnlyBufferException e) {
            System.out.println("compliant read-only view: put() rejected, hasArray = " + view.hasArray());
        }

        // Compliant: buffer over a copy
        CharBuffer copy = good.getCopy();
        copy.put(0, 'X');
        System.out.println("compliant copy:           caller sees " + copy.rewind() + ", internal still " + good.peek());
    }
}

class PasswordHolderNoncompliant {
    private final char[] password;

    PasswordHolderNoncompliant(char[] password) {
        this.password = password.clone();
    }

    CharBuffer getBuffer() {
        return CharBuffer.wrap(password);
    }

    CharBuffer getDuplicate() {
        return CharBuffer.wrap(password).duplicate();
    }

    String peek() { // for the demo only
        return new String(password);
    }
}

class PasswordHolderCompliant {
    private final char[] password;

    PasswordHolderCompliant(char[] password) {
        this.password = password.clone();
    }

    CharBuffer getReadOnlyBuffer() {
        return CharBuffer.wrap(password).asReadOnlyBuffer();
    }

    CharBuffer getCopy() {
        return CharBuffer.wrap(password.clone());
    }

    String peek() { // for the demo only
        return new String(password);
    }
}

/* Output:
noncompliant wrap():      internal now Xunter2
noncompliant duplicate(): internal now XYnter2, hasArray = true
compliant read-only view: put() rejected, hasArray = false
compliant copy:           caller sees Xunter2, internal still hunter2
*/
