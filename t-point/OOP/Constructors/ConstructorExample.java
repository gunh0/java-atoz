// A constructor initialises a new object. It has the class's name and no
// return type, and runs once per `new`.
//  - if a class declares no constructor, the compiler adds a no-arg default one
//  - as soon as you declare any constructor, that default one disappears
//  - constructors can be overloaded (different parameter lists)
//  - this(...) calls another constructor of the same class; it must be the
//    first statement, which lets all overloads funnel into one "real" one
//  - a copy constructor builds a new object from an existing one
public class ConstructorExample {
    public static void main(String[] args) {
        // Default constructor supplied by the compiler
        Point origin = new Point();
        System.out.println("default ctor: (" + origin.x + ", " + origin.y + ")");

        // Overloaded constructors, all chained to the full one
        Employee e1 = new Employee("Kim");
        Employee e2 = new Employee("Lee", "Security");
        Employee e3 = new Employee("Park", "Platform", 7200);
        System.out.println(e1);
        System.out.println(e2);
        System.out.println(e3);

        // Copy constructor: an independent object with the same state
        Employee copy = new Employee(e3);
        copy.salary += 500;
        System.out.println("copy:     " + copy);
        System.out.println("original: " + e3);
        System.out.println("same object: " + (copy == e3));

        // new Employee() would not compile: no no-arg constructor exists,
        // because Employee declares its own constructors.
    }
}

class Point {
    int x;
    int y;
    // no constructor declared: the compiler generates Point() {}
}

class Employee {
    final String name;
    final String team;
    int salary;

    Employee(String name) {
        this(name, "Unassigned");               // delegate
        System.out.println("  Employee(name) finished for " + name);
    }

    Employee(String name, String team) {
        this(name, team, 5000);                 // delegate again
        System.out.println("  Employee(name, team) finished for " + name);
    }

    // The one constructor that really sets the fields and validates them
    Employee(String name, String team, int salary) {
        if (salary < 0) {
            throw new IllegalArgumentException("negative salary");
        }
        this.name = name;
        this.team = team;
        this.salary = salary;
        System.out.println("  Employee(name, team, salary) finished for " + name);
    }

    // Copy constructor
    Employee(Employee other) {
        this(other.name, other.team, other.salary);
    }

    @Override
    public String toString() {
        return name + " [" + team + ", " + salary + "]";
    }
}

/* Output:
default ctor: (0, 0)
  Employee(name, team, salary) finished for Kim
  Employee(name, team) finished for Kim
  Employee(name) finished for Kim
  Employee(name, team, salary) finished for Lee
  Employee(name, team) finished for Lee
  Employee(name, team, salary) finished for Park
Kim [Unassigned, 5000]
Lee [Security, 5000]
Park [Platform, 7200]
  Employee(name, team, salary) finished for Park
copy:     Park [Platform, 7700]
original: Park [Platform, 7200]
same object: false
*/
