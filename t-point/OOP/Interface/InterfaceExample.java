// An interface declares a contract: what an object can do, not how. A class
// can implement many interfaces (but extend only one class).
//  - abstract methods: implementing classes must provide them
//  - default methods (Java 8): a body that implementers inherit, so an
//    interface can grow without breaking existing classes
//  - static methods: helpers called through the interface name
//  - fields are implicitly public static final constants
// If a class inherits the same default method from two interfaces, it must
// override it and may pick one with InterfaceName.super.method().
public class InterfaceExample {
    public static void main(String[] args) {
        Sensor thermometer = new Thermometer(21.5);
        System.out.println(thermometer.read() + " " + thermometer.unit());
        System.out.println(thermometer.describe());           // default method

        // Static interface method: called on the interface, not an instance
        System.out.println("celsius 30 -> fahrenheit " + Sensor.toFahrenheit(30));
        System.out.println("max sensors: " + Sensor.MAX_SENSORS);

        // A class implementing two interfaces with the same default method
        SmartPlug plug = new SmartPlug();
        System.out.println(plug.status());
        System.out.println("plug is a Sensor: " + (plug instanceof Sensor)
                + ", a Switchable: " + (plug instanceof Switchable));
    }
}

interface Sensor {
    int MAX_SENSORS = 16;                  // public static final

    double read();                         // abstract

    String unit();                         // abstract

    default String describe() {            // default: shared implementation
        return "sensor reading " + read() + " " + unit();
    }

    default String status() {
        return "sensor ok";
    }

    static double toFahrenheit(double celsius) {
        return celsius * 9 / 5 + 32;
    }
}

interface Switchable {
    void toggle();

    boolean isOn();

    default String status() {
        return isOn() ? "switch on" : "switch off";
    }
}

class Thermometer implements Sensor {
    private final double celsius;

    Thermometer(double celsius) {
        this.celsius = celsius;
    }

    @Override
    public double read() {                 // interface methods are public
        return celsius;
    }

    @Override
    public String unit() {
        return "C";
    }
}

class SmartPlug implements Sensor, Switchable {
    private boolean on = true;

    @Override public double read() { return on ? 42.0 : 0.0; }
    @Override public String unit() { return "W"; }
    @Override public void toggle() { on = !on; }
    @Override public boolean isOn() { return on; }

    // Both interfaces supply status(): the compiler forces us to resolve it
    @Override
    public String status() {
        return Switchable.super.status() + ", " + Sensor.super.status() + ", " + describe();
    }
}

/* Output:
21.5 C
sensor reading 21.5 C
celsius 30 -> fahrenheit 86.0
max sensors: 16
switch on, sensor ok, sensor reading 42.0 W
plug is a Sensor: true, a Switchable: true
*/
