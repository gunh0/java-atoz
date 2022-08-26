// `super` refers to the superclass part of the current object. It is used to:
//  1. call a superclass constructor: super(...), first statement only; if you
//     write nothing, the compiler inserts super() for you
//  2. call the superclass version of an overridden method: super.method()
//  3. reach a superclass field hidden by a subclass field: super.field
// A common pattern is to extend, not replace, inherited behaviour by calling
// super.method() and then adding to its result.
public class SuperKeywordExample {
    public static void main(String[] args) {
        Manager m = new Manager("Choi", 6000, 1500);
        System.out.println(m.describe());
        System.out.println("annual pay: " + m.annualPay());
        m.showLabels();

        System.out.println("--- implicit super()");
        new Intern();
    }
}

class Employee {
    protected String label = "employee";
    protected final String name;
    protected final int monthlySalary;

    Employee(String name, int monthlySalary) {
        System.out.println("Employee(" + name + ")");
        this.name = name;
        this.monthlySalary = monthlySalary;
    }

    Employee() {
        this("anonymous", 0);
    }

    int annualPay() {
        return monthlySalary * 12;
    }

    String describe() {
        return name + " earns " + monthlySalary + "/month";
    }
}

class Manager extends Employee {
    protected String label = "manager";  // hides Employee.label (avoid in real code)
    private final int bonus;

    Manager(String name, int monthlySalary, int bonus) {
        super(name, monthlySalary);      // use 1: pick the superclass constructor
        System.out.println("Manager(" + name + ")");
        this.bonus = bonus;
    }

    @Override
    int annualPay() {
        return super.annualPay() + bonus; // use 2: reuse the inherited logic
    }

    @Override
    String describe() {
        return super.describe() + " plus a bonus of " + bonus;
    }

    void showLabels() {
        System.out.println("label = " + label + ", super.label = " + super.label); // use 3
    }
}

class Intern extends Employee {
    Intern() {
        // no explicit call: the compiler inserts super(), i.e. Employee()
        System.out.println("Intern()");
    }
}

/* Output:
Employee(Choi)
Manager(Choi)
Choi earns 6000/month plus a bonus of 1500
annual pay: 73500
label = manager, super.label = employee
--- implicit super()
Employee(anonymous)
Intern()
*/
