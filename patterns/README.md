# patterns

> The Gang of Four design patterns, one small program per pattern.

Runnable notes on the 23 patterns from *Design Patterns: Elements of Reusable Object-Oriented Software* (Gamma, Helm, Johnson, Vlissides), grouped the way the book groups them: **creational** patterns decide how objects get created, **structural** patterns decide how classes and objects are composed into larger structures, and **behavioral** patterns decide how objects divide up work and talk to each other.

Files live in one directory per group and are named after the pattern: `<creational|structural|behavioral>/<Pattern>.java`, e.g. `structural/Decorator.java`. Each file states the pattern's intent in its header comment, applies it to a small realistic scenario instead of the usual shapes and animals, and ends with an `/* Output: */` block that `scripts/check.sh` verifies. Where the JDK itself uses the pattern (the `java.io` stream decorators, `Integer.valueOf` as a flyweight factory, `Collections.unmodifiableList` as a protection proxy, ...) the file points it out. Run one with `java <File>.java` from its directory.

<!-- INDEX:START -->
- Creational, Singleton (eager, synchronized, double-checked, holder, enum): [`creational/Singleton.java`](creational/Singleton.java)
- Creational, Factory Method: [`creational/FactoryMethod.java`](creational/FactoryMethod.java)
- Creational, Abstract Factory: [`creational/AbstractFactory.java`](creational/AbstractFactory.java)
- Creational, Builder (director and two representations): [`creational/Builder.java`](creational/Builder.java)
- Creational, Prototype: [`creational/Prototype.java`](creational/Prototype.java)
- Structural, Adapter: [`structural/Adapter.java`](structural/Adapter.java)
- Structural, Bridge: [`structural/Bridge.java`](structural/Bridge.java)
- Structural, Composite: [`structural/Composite.java`](structural/Composite.java)
- Structural, Decorator (as in `java.io` streams): [`structural/Decorator.java`](structural/Decorator.java)
- Structural, Facade: [`structural/Facade.java`](structural/Facade.java)
<!-- INDEX:END -->
