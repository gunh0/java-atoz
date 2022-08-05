// Method overloading: several methods share a name but differ in their
// parameter lists (number, types or order). The compiler picks one at
// COMPILE time from the static types of the arguments. The return type alone
// cannot distinguish overloads.
//
// When no exact match exists, the compiler tries, in this order:
//   1. exact match or primitive widening (int -> long -> double)
//   2. boxing / unboxing (int -> Integer -> Object)
//   3. varargs
// The first phase that finds a candidate wins, even if a later phase would
// look like a "better" fit.
public class MethodOverloadingExample {
    public static void main(String[] args) {
        // Overloads that differ in arity and in types
        System.out.println(Area.of(3));          // square
        System.out.println(Area.of(3, 4));       // rectangle
        System.out.println(Area.of(1.5));        // circle

        // Resolution order
        byte b = 10;
        Resolver.call(b);          // no call(byte): widens to call(int)
        Resolver.call(5L);         // exact match: call(long)
        Resolver.call('A');        // char widens to int
        Integer boxed = 7;
        Resolver.call(boxed);      // exact match on the reference type Integer

        Tricky.pick(1);            // widening (long) beats boxing (Integer)
        Tricky.pick2(1);           // boxing (Integer) beats varargs (int...)
        Tricky.pick2();            // only varargs can take zero arguments

        // The static type decides, not the runtime type
        Object text = "hello";
        Tricky.describe(text);
        Tricky.describe("hello");
    }
}

class Area {
    static String of(int side) {
        return "square " + side + "x" + side + " = " + (side * side);
    }

    static String of(int width, int height) {
        return "rectangle " + width + "x" + height + " = " + (width * height);
    }

    static String of(double radius) {
        return String.format("circle r=%.1f = %.2f", radius, Math.PI * radius * radius);
    }
    // int of(int side) { ... }  would not compile: differs only in return type
}

class Resolver {
    static void call(int x)      { System.out.println("call(int) " + x); }
    static void call(long x)     { System.out.println("call(long) " + x); }
    static void call(Integer x)  { System.out.println("call(Integer) " + x); }
}

class Tricky {
    static void pick(long x)     { System.out.println("pick(long)"); }
    static void pick(Integer x)  { System.out.println("pick(Integer)"); }

    static void pick2(Integer x) { System.out.println("pick2(Integer)"); }
    static void pick2(int... xs) { System.out.println("pick2(int...) with " + xs.length + " args"); }

    static void describe(Object o) { System.out.println("describe(Object)"); }
    static void describe(String s) { System.out.println("describe(String)"); }
}

/* Output:
square 3x3 = 9
rectangle 3x4 = 12
circle r=1.5 = 7.07
call(int) 10
call(long) 5
call(int) 65
call(Integer) 7
pick(long)
pick2(Integer)
pick2(int...) with 0 args
describe(Object)
describe(String)
*/
