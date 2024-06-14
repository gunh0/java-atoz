// Method handles: typed, directly executable references to methods
//
// java.lang.invoke.MethodHandle is the JVM-level function pointer behind
// lambdas, string concatenation and modern reflection. You get one from a
// Lookup, which carries the access rights of the class that created it:
// access is checked ONCE, when the handle is looked up, not on every call,
// and a Lookup can be handed to a framework to delegate exactly those rights.
// Every handle has a MethodType (return type + parameter types). invokeExact
// requires the call site's static types to match it exactly (no boxing, no
// widening; the JIT can then call it like a direct call); invoke adapts
// types like asType would. Handles compose: bindTo fixes the receiver,
// insertArguments fixes leading arguments, filterReturnValue post-processes.
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;

public class MethodHandlesBasics {
    static class Account {
        private long balance;
        private final String owner;
        Account(String owner, long balance) { this.owner = owner; this.balance = balance; }
        long deposit(long amount) { return balance += amount; }
        static String format(String owner, long cents) { return owner + ": " + cents / 100 + "." + String.format("%02d", cents % 100); }
    }

    public static void main(String[] args) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup(); // our own access rights

        // Constructor, virtual method, static method, private field getter
        MethodHandle ctor = lookup.findConstructor(Account.class, MethodType.methodType(void.class, String.class, long.class));
        MethodHandle deposit = lookup.findVirtual(Account.class, "deposit", MethodType.methodType(long.class, long.class));
        MethodHandle format = lookup.findStatic(Account.class, "format", MethodType.methodType(String.class, String.class, long.class));
        MethodHandle balance = lookup.findGetter(Account.class, "balance", long.class);

        System.out.println("deposit type: " + deposit.type()); // receiver becomes the first parameter
        Account acct = (Account) ctor.invokeExact("alice", 1_000L);
        long after = (long) deposit.invokeExact(acct, 250L);
        System.out.println("after deposit: " + after + ", via getter: " + (long) balance.invokeExact(acct));
        System.out.println((String) format.invokeExact("alice", after));

        // invokeExact is strict: an int argument or an Object result is a different type
        try {
            long r = (long) deposit.invokeExact(acct, 5);   // int, not long
        } catch (WrongMethodTypeException e) {
            System.out.println("invokeExact with an int argument: " + e.getClass().getSimpleName());
        }
        Object boxed = deposit.invoke(acct, 5);             // invoke converts: int -> long, long -> Long
        System.out.println("invoke with conversions returns " + boxed.getClass().getSimpleName() + " " + boxed);

        // Composition
        MethodHandle depositToAcct = deposit.bindTo(acct);                         // (long)long
        MethodHandle formatAlice = MethodHandles.insertArguments(format, 0, "alice"); // (long)String
        MethodHandle depositAndShow = MethodHandles.filterReturnValue(depositToAcct, formatAlice);
        System.out.println("composed type " + depositAndShow.type() + ": " + (String) depositAndShow.invokeExact(100L));

        // Access is decided by the Lookup: the public lookup cannot see private members
        try {
            MethodHandles.publicLookup().findGetter(Account.class, "balance", long.class);
        } catch (IllegalAccessException e) {
            System.out.println("publicLookup on a private field: " + e.getClass().getSimpleName());
        }
        // ...and a final field has a getter but no setter
        try {
            lookup.findSetter(Account.class, "owner", String.class);
        } catch (IllegalAccessException e) {
            System.out.println("setter for a final field: " + e.getClass().getSimpleName());
        }
    }
}

/* Output:
deposit type: (Account,long)long
after deposit: 1250, via getter: 1250
alice: 12.50
invokeExact with an int argument: WrongMethodTypeException
invoke with conversions returns Long 1255
composed type (long)String: alice: 13.55
publicLookup on a private field: IllegalAccessException
setter for a final field: IllegalAccessException
*/
