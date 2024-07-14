# jvm

> What the virtual machine does underneath your code, observed from Java.

Java source becomes class files, class files are loaded by class loaders, verified, linked and initialized, and their bytecode is interpreted and then compiled by the JIT; memory is managed by a garbage collector, and the Java Memory Model defines what one thread is guaranteed to see of another's writes. This section looks at each of those layers from plain Java code: class loaders and class identity, the class-file format and bytecode, method handles and `invokedynamic`, happens-before, weak/soft/phantom references and `Cleaner`, the management beans and JDK Flight Recorder.

The JVM is allowed to do a lot behind your back (garbage collection timing, JIT compilation, inlining), so the programs only print what the specification guarantees, or a verdict that holds on any conforming run; where something is inherently timing-dependent the header explains it and the output avoids it. Each file is a single-file program, run with `java <File>.java` from its directory; classes that need to be loaded "from outside" are compiled in memory with `javax.tools`.

Related notes elsewhere: [StackWalker](../modern-java/java9/StackWalkerApi.java), [VarHandles](../modern-java/java9/VarHandles.java), [hidden classes](../modern-java/java15/HiddenClasses.java), [JFR event streaming](../modern-java/java14/JfrEventStreaming.java), [generational ZGC](../modern-java/java21/GenerationalZgc.java) and [Effective Java item 8](../effective/08_FinalizerAttack.java) (finalizers and cleaners).

<!-- INDEX:START -->
- ClassLoading, class loader hierarchy and parent-first delegation: [`ClassLoading/ClassLoaderHierarchy.java`](ClassLoading/ClassLoaderHierarchy.java)
- ClassLoading, loading vs initialization: when static initializers run: [`ClassLoading/ClassInitialization.java`](ClassLoading/ClassInitialization.java)
- ClassLoading, a custom `ClassLoader` for classes compiled in memory: [`ClassLoading/InMemoryClassLoader.java`](ClassLoading/InMemoryClassLoader.java)
- ClassLoading, class identity: name plus defining loader: [`ClassLoading/ClassIdentity.java`](ClassLoading/ClassIdentity.java)
- ClassLoading, parent-first vs child-first loading, protected `java.*`: [`ClassLoading/ChildFirstLoader.java`](ClassLoading/ChildFirstLoader.java)
- ClassFile, inside a class file: a small hand-written parser: [`ClassFile/ClassFileHeader.java`](ClassFile/ClassFileHeader.java)
- ClassFile, reading bytecode with `javap` through `ToolProvider`: [`ClassFile/JavapBytecode.java`](ClassFile/JavapBytecode.java)
- Invoke, method handles: lookup, `invokeExact`, composition: [`Invoke/MethodHandlesBasics.java`](Invoke/MethodHandlesBasics.java)
- Invoke, lambdas, `invokedynamic` and `LambdaMetafactory`: [`Invoke/LambdaMetafactoryDemo.java`](Invoke/LambdaMetafactoryDemo.java)
- Jmm, JMM: visibility and publication with `volatile`: [`Jmm/VolatileVisibility.java`](Jmm/VolatileVisibility.java)
- Jmm, JMM: happens-before edges: [`Jmm/HappensBefore.java`](Jmm/HappensBefore.java)
<!-- INDEX:END -->
