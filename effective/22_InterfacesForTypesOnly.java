// Effective Java, Item 22: Use interfaces only to define types.
//
// Implementing an interface should say something about what instances can
// DO. The "constant interface" pattern (an interface holding only static
// final fields, implemented to avoid qualifying the names) abuses this: the
// constants become part of the class's public API, every subclass inherits
// them into its namespace, and "instanceof PhysicalConstants" is true for
// objects that have nothing to do with physics. Put constants where they
// belong: in the class or interface they are tied to (Integer.MAX_VALUE), in
// an enum, or in a noninstantiable utility class.
import java.util.Arrays;

public class InterfacesForTypesOnly {
    public static void main(String[] args) {
        // Constant interface: implementation detail leaks into the type
        Molecule water = new Molecule(18.015);
        System.out.println("Molecule instanceof PhysicalConstants: " + (water instanceof PhysicalConstants));
        System.out.println("constant reachable through the class: " + Molecule.AVOGADROS_NUMBER);
        System.out.println("and through every subclass: " + HeavyWater.AVOGADROS_NUMBER);
        System.out.println("Molecule's interfaces: " + Arrays.toString(Molecule.class.getInterfaces()));

        // Utility class: same constants, no type pollution
        CleanMolecule clean = new CleanMolecule(18.015);
        System.out.println("CleanMolecule's interfaces: " + Arrays.toString(CleanMolecule.class.getInterfaces()));
        System.out.printf("molecules in 1 g of water: %.4e%n", water.moleculesIn(1.0));
        System.out.printf("molecules in 1 g of water: %.4e%n", clean.moleculesIn(1.0));
    }
}

// Anti-pattern
interface PhysicalConstants {
    double AVOGADROS_NUMBER = 6.022_140_76e23;
    double BOLTZMANN_CONSTANT = 1.380_649e-23;
}

class Molecule implements PhysicalConstants {
    private final double gramsPerMole;

    Molecule(double gramsPerMole) {
        this.gramsPerMole = gramsPerMole;
    }

    double moleculesIn(double grams) {
        return grams / gramsPerMole * AVOGADROS_NUMBER;
    }
}

class HeavyWater extends Molecule {
    HeavyWater() {
        super(20.028);
    }
}

// Recommended: constants in a noninstantiable utility class
final class Physics {
    private Physics() {
    }

    static final double AVOGADROS_NUMBER = 6.022_140_76e23;
    static final double BOLTZMANN_CONSTANT = 1.380_649e-23;
}

class CleanMolecule {
    private final double gramsPerMole;

    CleanMolecule(double gramsPerMole) {
        this.gramsPerMole = gramsPerMole;
    }

    double moleculesIn(double grams) {
        return grams / gramsPerMole * Physics.AVOGADROS_NUMBER;
    }
}

/* Output:
Molecule instanceof PhysicalConstants: true
constant reachable through the class: 6.02214076E23
and through every subclass: 6.02214076E23
Molecule's interfaces: [interface PhysicalConstants]
CleanMolecule's interfaces: []
molecules in 1 g of water: 3.3428e+22
molecules in 1 g of water: 3.3428e+22
*/
