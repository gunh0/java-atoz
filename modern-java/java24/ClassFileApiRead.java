// JEP 484 — Class-File API: reading class files (Java 24)
//
// Frameworks, agents and tools read and rewrite bytecode all the time, and
// for twenty years that meant a third-party library (ASM, BCEL, Javassist)
// or a hand-written parser (jvm/ClassFile/ClassFileHeader.java). The JDK
// itself bundled a private copy of ASM, which always lagged behind the newest
// class-file version: a tool built on ASM N could not read classes from the
// next Java release until ASM caught up. java.lang.classfile is the standard
// replacement, versioned with the JDK. ClassFile.of().parse(bytes) returns an
// immutable ClassModel: fields, methods, attributes and, per method, a
// CodeModel whose elements are instructions, labels and exception handlers,
// all as sealed interfaces and records that work well with pattern matching
// for switch. Here a class is compiled in memory with javax.tools and then
// inspected: header, members, and the calls made by one method.
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.reflect.AccessFlag;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public class ClassFileApiRead {
    static final String SOURCE = """
            import java.util.*;
            public class Inventory implements Comparable<Inventory> {
                private static final int LIMIT = 1000;
                private final Map<String, Integer> stock = new HashMap<>();
                public void add(String item, int qty) {
                    if (qty <= 0 || qty > LIMIT) throw new IllegalArgumentException("bad quantity");
                    stock.merge(item, qty, Integer::sum);
                }
                public int total() { return stock.values().stream().mapToInt(Integer::intValue).sum(); }
                public int compareTo(Inventory o) { return Integer.compare(total(), o.total()); }
            }
            """;

    public static void main(String[] args) throws Exception {
        byte[] bytes = compileInMemory("Inventory", SOURCE).get("Inventory");
        ClassModel cm = ClassFile.of().parse(bytes);

        System.out.println("class      " + cm.thisClass().asInternalName() + " (class file version " + cm.majorVersion() + ")");
        System.out.println("flags      " + cm.flags().flags());
        System.out.println("extends    " + cm.superclass().map(c -> c.asInternalName()).orElse("-"));
        System.out.println("implements " + cm.interfaces().stream().map(c -> c.asInternalName()).toList());
        for (FieldModel f : cm.fields()) {
            System.out.println("field      " + f.fieldName() + " " + f.fieldType() + " " + f.flags().flags());
        }
        for (MethodModel m : cm.methods()) {
            String flags = m.flags().flags().stream().map(AccessFlag::name).sorted().collect(Collectors.joining(" "));
            System.out.println("method     " + m.methodName() + m.methodType() + " [" + flags + "]");
        }
        System.out.println("attributes " + cm.attributes().stream().map(a -> a.attributeName().stringValue()).toList());

        // Walk the code of add(String, int) with pattern matching over its elements
        MethodModel add = cm.methods().stream().filter(m -> m.methodName().equalsString("add")).findFirst().orElseThrow();
        CodeModel code = add.code().orElseThrow();
        System.out.println("add(): calls, field accesses and constants");
        for (var element : code) {
            switch (element) {
                case InvokeInstruction i -> System.out.println("  " + i.opcode().name().toLowerCase() + " "
                        + i.owner().asInternalName() + "." + i.name() + i.type());
                case FieldInstruction f -> System.out.println("  " + f.opcode().name().toLowerCase() + " "
                        + f.owner().asInternalName() + "." + f.name() + " " + f.type());
                case InvokeDynamicInstruction d -> System.out.println("  invokedynamic " + d.name() + d.typeSymbol().descriptorString()
                        + " via " + d.bootstrapMethod().owner().displayName() + "." + d.bootstrapMethod().methodName());
                case ConstantInstruction c -> System.out.println("  constant " + c.constantValue());
                default -> { }
            }
        }
    }

    // javac into memory: no files written anywhere
    static Map<String, byte[]> compileInMemory(String className, String source) {
        Map<String, ByteArrayOutputStream> out = new TreeMap<>();
        var javac = ToolProvider.getSystemJavaCompiler();
        JavaFileManager fm = new ForwardingJavaFileManager<>(javac.getStandardFileManager(null, null, null)) {
            @Override
            public JavaFileObject getJavaFileForOutput(Location loc, String name, JavaFileObject.Kind kind, FileObject sibling) {
                return new SimpleJavaFileObject(URI.create("mem:///" + name + ".class"), kind) {
                    @Override public OutputStream openOutputStream() {
                        return out.computeIfAbsent(name, k -> new ByteArrayOutputStream());
                    }
                };
            }
        };
        JavaFileObject src = new SimpleJavaFileObject(URI.create("string:///" + className + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignore) { return source; }
        };
        if (!javac.getTask(null, fm, null, List.of("--release", "21"), null, List.of(src)).call()) {
            throw new IllegalStateException("compilation failed");
        }
        Map<String, byte[]> classes = new TreeMap<>();
        out.forEach((k, v) -> classes.put(k, v.toByteArray()));
        return classes;
    }
}

/* Output:
class      Inventory (class file version 65)
flags      [PUBLIC, SUPER]
extends    java/lang/Object
implements [java/lang/Comparable]
field      LIMIT I [PRIVATE, STATIC, FINAL]
field      stock Ljava/util/Map; [PRIVATE, FINAL]
method     <init>()V [PUBLIC]
method     add(Ljava/lang/String;I)V [PUBLIC]
method     total()I [PUBLIC]
method     compareTo(LInventory;)I [PUBLIC]
method     compareTo(Ljava/lang/Object;)I [BRIDGE PUBLIC SYNTHETIC]
attributes [Signature, SourceFile, BootstrapMethods, InnerClasses]
add(): calls, field accesses and constants
  constant 1000
  constant bad quantity
  invokespecial java/lang/IllegalArgumentException.<init>(Ljava/lang/String;)V
  getfield Inventory.stock Ljava/util/Map;
  invokestatic java/lang/Integer.valueOf(I)Ljava/lang/Integer;
  invokedynamic apply()Ljava/util/function/BiFunction; via LambdaMetafactory.metafactory
  invokeinterface java/util/Map.merge(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;
*/
