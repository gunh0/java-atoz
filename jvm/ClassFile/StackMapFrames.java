// Stack map frames: what the verifier checks, read and generated with java.lang.classfile
//
// Before a class runs, the verifier proves its bytecode type-safe: no int
// used as a reference, no stack underflow, no uninitialized object used.
// The old verifier inferred types by data-flow iteration over the whole
// method; since class-file version 50 (Java 6), and mandatory from 51
// (Java 7), the compiler must write a StackMapTable attribute that states the
// types of locals and operand-stack slots at every branch target and handler,
// and the verifier only checks each instruction against them in one linear
// pass. Frames are compressed relative to the previous one (same, same_locals_1
// _stack_item, chop, append, full_frame). They are why hand-written bytecode
// is hard: every jump target needs a correct frame. The Class-File API
// (JEP 484, Java 24) reads them as StackMapFrameInfo and, by default,
// generates them when it builds a class; with DROP_STACK_MAPS the same
// method is rejected with a VerifyError the moment it is linked.
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.StackMapFrameInfo;
import java.lang.classfile.attribute.StackMapFrameInfo.ObjectVerificationTypeInfo;
import java.lang.classfile.attribute.StackMapFrameInfo.SimpleVerificationTypeInfo;
import java.lang.classfile.attribute.StackMapFrameInfo.VerificationTypeInfo;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.spi.ToolProvider;

import static java.lang.constant.ConstantDescs.CD_int;

public class StackMapFrames {
    static final String SOURCE = """
            public class Frames {
                static int sumPositive(int[] a) {
                    int s = 0;
                    for (int i = 0; i < a.length; i++) {
                        if (a[i] > 0) s += a[i];
                    }
                    return s;
                }
                static Object pick(boolean b) {
                    Object o;
                    if (b) o = "text"; else o = Integer.valueOf(1);
                    return o;
                }
            }
            """;

    public static void main(String[] args) throws Throwable {
        // 1. Read the frames javac wrote
        ClassModel cm = ClassFile.of().parse(compile());
        System.out.println("class file version " + cm.majorVersion());
        for (MethodModel m : cm.methods()) {
            if (m.methodName().stringValue().equals("<init>")) continue;
            CodeAttribute code = m.findAttribute(Attributes.code()).orElseThrow();
            System.out.println(m.methodName().stringValue() + m.methodType().stringValue() + ", code length " + code.codeLength());
            for (StackMapFrameInfo f : code.findAttribute(Attributes.stackMapTable()).orElseThrow().entries()) {
                System.out.printf("  bci %2d  %-22s locals %-24s stack %s%n", code.labelToBci(f.target()),
                        kind(f.frameType()), types(f.locals()), types(f.stack()));
            }
        }

        // 2. Generate static int abs(int x) { if (x >= 0) goto L; return -x; L: return x; }
        byte[] withFrames = absClass(ClassFile.of());
        byte[] withoutFrames = absClass(ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS));
        System.out.println("generated, frames: " + frameCount(withFrames) + " -> verify() problems: " + ClassFile.of().verify(withFrames).size());
        System.out.println("generated, frames dropped: " + frameCount(withoutFrames) + " -> verify() problems: " + ClassFile.of().verify(withoutFrames).size());
        System.out.println("load + call abs(-5) with frames:    " + call(withFrames));
        System.out.println("load + call abs(-5) without frames: " + call(withoutFrames));
    }

    static byte[] absClass(ClassFile cf) {
        return cf.build(ClassDesc.of("Abs"), clb -> clb.withMethodBody("abs", MethodTypeDesc.of(CD_int, CD_int),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, cb -> {
                    Label nonNegative = cb.newLabel();
                    cb.iload(0).ifge(nonNegative)
                      .iload(0).ineg().ireturn()
                      .labelBinding(nonNegative)   // a branch target: needs a frame
                      .iload(0).ireturn();
                }));
    }

    static int frameCount(byte[] bytes) {
        MethodModel m = ClassFile.of().parse(bytes).methods().getFirst();
        return m.findAttribute(Attributes.code()).orElseThrow().findAttribute(Attributes.stackMapTable())
                .map(t -> t.entries().size()).orElse(0);
    }

    static String call(byte[] bytes) throws Throwable {
        // Each class gets its own hidden class so the two versions do not clash
        try {
            Class<?> c = MethodHandles.lookup().defineHiddenClass(bytes, true).lookupClass();
            return String.valueOf(c.getMethod("abs", int.class).invoke(null, -5));
        } catch (VerifyError e) {
            return "VerifyError";
        } catch (InvocationTargetException e) {
            return e.getCause().getClass().getSimpleName();
        }
    }

    static String kind(int type) {
        if (type < 64) return "same";
        if (type < 128) return "same_locals_1_stack";
        if (type == 247) return "same_locals_1_stack_ext";
        if (type >= 248 && type <= 250) return "chop " + (251 - type);
        if (type == 251) return "same_extended";
        if (type >= 252 && type <= 254) return "append " + (type - 251);
        return "full_frame";
    }

    static String types(List<VerificationTypeInfo> types) {
        return types.stream().map(t -> switch (t) {
            case SimpleVerificationTypeInfo s -> s.name().toLowerCase();
            case ObjectVerificationTypeInfo o -> o.classSymbol().displayName();
            default -> "uninitialized";
        }).toList().toString();
    }

    static byte[] compile() throws Exception {
        Path dir = Files.createTempDirectory("frames");
        Path src = dir.resolve("Frames.java");
        Path cls = dir.resolve("Frames.class");
        try {
            Files.writeString(src, SOURCE);
            StringWriter log = new StringWriter();
            int rc = ToolProvider.findFirst("javac").orElseThrow()
                    .run(new PrintWriter(log), new PrintWriter(log), "--release", "17", "-d", dir.toString(), src.toString());
            if (rc != 0) throw new IllegalStateException(log.toString());
            return Files.readAllBytes(cls);
        } finally {
            Files.deleteIfExists(cls);
            Files.deleteIfExists(src);
            Files.delete(dir);
        }
    }
}

/* Output:
class file version 61
sumPositive([I)I, code length 30
  bci  4  append 2               locals [int[], integer, integer] stack []
  bci 22  same                   locals [int[], integer, integer] stack []
  bci 28  chop 1                 locals [int[], integer]         stack []
pick(Z)Ljava/lang/Object;, code length 17
  bci 10  same                   locals [integer]                stack []
  bci 15  append 1               locals [integer, Object]        stack []
generated, frames: 1 -> verify() problems: 0
generated, frames dropped: 0 -> verify() problems: 1
load + call abs(-5) with frames:    5
load + call abs(-5) without frames: VerifyError
*/
