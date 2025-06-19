// JEP 484 — Class-File API: generating and transforming classes (Java 24)
//
// Generating bytecode used to mean ASM's visitor API: call visitMethod,
// visitVarInsn, visitMaxs in the right order, ask ClassWriter to compute
// frames, and hope the result verifies. java.lang.classfile builds classes
// from lambdas instead: ClassFile.build(ClassDesc, classBuilder -> ...) with
// withField / withMethodBody, and a CodeBuilder with one method per
// instruction (aload, getfield, invokevirtual, areturn, ...) that tracks
// max stack/locals and generates stack map frames for you. Symbolic
// descriptors (ClassDesc, MethodTypeDesc from java.lang.constant) replace
// hand-written strings such as "(Ljava/lang/String;)V". Transformation is the
// same model in reverse: transformClass(model, ClassTransform) streams every
// element of an existing class through your code, which keeps, drops,
// replaces or adds elements. Below, a class is built from nothing, verified,
// loaded and called; then a copy is transformed to drop a method and to
// rewrite a string constant.
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Method;
import java.util.Arrays;

import static java.lang.classfile.ClassFile.ACC_FINAL;
import static java.lang.classfile.ClassFile.ACC_PRIVATE;
import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_void;

public class ClassFileApiGenerate {
    static final ClassDesc GREETER = ClassDesc.of("demo.Greeter");

    // public class demo.Greeter {
    //     private final String name;
    //     public Greeter(String name) { this.name = name; }
    //     public String greet() { return "Hello, ".concat(name); }
    //     public static int square(int x) { return x * x; }
    // }
    static byte[] buildGreeter() {
        return ClassFile.of().build(GREETER, clb -> clb
                .withFlags(ACC_PUBLIC)
                .withField("name", CD_String, ACC_PRIVATE | ACC_FINAL)
                .withMethodBody(ConstantDescs.INIT_NAME, MethodTypeDesc.of(CD_void, CD_String), ACC_PUBLIC, cb -> cb
                        .aload(0)
                        .invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                        .aload(0)
                        .aload(1)
                        .putfield(GREETER, "name", CD_String)
                        .return_())
                .withMethodBody("greet", MethodTypeDesc.of(CD_String), ACC_PUBLIC, cb -> cb
                        .ldc("Hello, ")
                        .aload(0)
                        .getfield(GREETER, "name", CD_String)
                        .invokevirtual(CD_String, "concat", MethodTypeDesc.of(CD_String, CD_String))
                        .areturn())
                .withMethodBody("square", MethodTypeDesc.of(CD_int, CD_int), ACC_PUBLIC | ACC_STATIC, cb -> cb
                        .iload(0)
                        .iload(0)
                        .imul()
                        .ireturn()));
    }

    // Each version gets its own loader, since both are called demo.Greeter
    static Class<?> load(byte[] bytes) {
        return new ClassLoader(ClassFileApiGenerate.class.getClassLoader()) {
            Class<?> define() { return defineClass(null, bytes, 0, bytes.length); }
        }.define();
    }

    static String methods(Class<?> c) {
        return Arrays.toString(Arrays.stream(c.getDeclaredMethods()).map(Method::getName).sorted().toArray());
    }

    public static void main(String[] args) throws Exception {
        byte[] original = buildGreeter();
        System.out.println("built demo.Greeter, verify() problems: " + ClassFile.of().verify(original).size());
        Class<?> c = load(original);
        Object g = c.getConstructor(String.class).newInstance("Duke");
        System.out.println(c.getName() + " methods " + methods(c));
        System.out.println("greet()   -> " + c.getMethod("greet").invoke(g));
        System.out.println("square(7) -> " + c.getMethod("square", int.class).invoke(null, 7));

        // Transform: drop square(), and in every method replace the constant "Hello, " with "Hi, "
        CodeTransform rewriteGreeting = (codeBuilder, element) -> {
            if (element instanceof ConstantInstruction ci && "Hello, ".equals(ci.constantValue())) {
                codeBuilder.ldc("Hi, ");
            } else {
                codeBuilder.with(element);
            }
        };
        ClassTransform transform = ClassTransform
                .dropping(e -> e instanceof MethodModel m && m.methodName().equalsString("square"))
                .andThen(ClassTransform.transformingMethodBodies(rewriteGreeting));
        byte[] transformed = ClassFile.of().transformClass(ClassFile.of().parse(original), transform);

        Class<?> t = load(transformed);
        System.out.println("transformed, verify() problems: " + ClassFile.of().verify(transformed).size()
                + ", methods " + methods(t));
        System.out.println("greet()   -> " + t.getMethod("greet").invoke(t.getConstructor(String.class).newInstance("Duke")));
    }
}

/* Output:
built demo.Greeter, verify() problems: 0
demo.Greeter methods [greet, square]
greet()   -> Hello, Duke
square(7) -> 49
transformed, verify() problems: 0, methods [greet]
greet()   -> Hi, Duke
*/
