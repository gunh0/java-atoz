// How switch is compiled: tableswitch, lookupswitch, strings and enums
//
// The JVM has two switch instructions, both on int keys: tableswitch, a jump
// table indexed by (key - low), used when the case values are dense, and
// lookupswitch, a sorted key/offset list searched (binary search in HotSpot)
// when they are sparse (note key 4 below: a hole in a table jumps to
// default). Everything else is lowered onto them by javac:
//  - a switch on String first switches on s.hashCode(), then confirms the
//    match with equals() (hash collisions such as "Aa"/"BB" share a bucket),
//    producing a case index that feeds a second, dense tableswitch;
//  - a switch on an enum does not use ordinal() directly, because the enum
//    may be recompiled with its constants reordered. javac generates a
//    synthetic class (Outer$1) with an int[] $SwitchMap$..., filled at class
//    initialization from the current ordinals, and switches on
//    $SwitchMap[e.ordinal()], so the client survives such a change (the map
//    holds 1, 2, ... in case order: RED -> 1, BLUE -> 2).
// The program compiles a class in memory and prints each method's switch
// skeleton from javap: the instructions that matter and the case keys.
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

public class SwitchLowering {
    static final String SOURCE = """
            enum Color { RED, GREEN, BLUE }
            public class Sw {
                static int dense(int x) {
                    switch (x) { case 1: return 10; case 2: return 20; case 3: return 30; case 5: return 50; default: return -1; }
                }
                static int sparse(int x) {
                    switch (x) { case 1: return 1; case 1000: return 2; case 1000000: return 3; default: return 0; }
                }
                static int text(String s) {
                    switch (s) { case "Aa": return 1; case "BB": return 2; case "cat": return 3; default: return 0; }
                }
                static int color(Color c) {
                    switch (c) { case RED: return 1; case BLUE: return 3; default: return 0; }
                }
            }
            """;

    public static void main(String[] args) throws Exception {
        System.out.println("\"Aa\".hashCode() = " + "Aa".hashCode() + ", \"BB\".hashCode() = " + "BB".hashCode()
                + ", \"cat\".hashCode() = " + "cat".hashCode());
        Path dir = Files.createTempDirectory("switch");
        try {
            Path src = dir.resolve("Sw.java");
            Files.writeString(src, SOURCE);
            tool("javac", "--release", "17", "-d", dir.toString(), src.toString());
            try (Stream<Path> s = Files.list(dir)) {
                System.out.println("class files: " + s.map(p -> p.getFileName().toString())
                        .filter(n -> n.endsWith(".class")).sorted().toList());
            }
            String javap = tool("javap", "-c", "-p", dir.resolve("Sw.class").toString());
            for (String m : List.of("dense", "sparse", "text", "color")) {
                System.out.println(m + "():");
                skeleton(javap, " " + m + "(");
            }
        } finally {
            try (Stream<Path> s = Files.walk(dir)) {
                for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
    }

    static void skeleton(String javap, String method) {
        boolean in = false;
        List<String> keys = null;
        String switchOp = null;
        for (String line : javap.lines().toList()) {
            String t = line.strip();
            if (t.endsWith(");")) { in = t.contains(method); continue; }
            if (!in) continue;
            if (keys != null) {
                if (t.equals("}")) {
                    System.out.println("    " + switchOp + " keys " + keys);
                    keys = null;
                } else if (!t.startsWith("default")) {
                    keys.add(t.substring(0, t.indexOf(':')));
                }
            } else if (t.matches("\\d+: (tableswitch|lookupswitch).*")) {
                switchOp = t.replaceFirst("^\\d+: (\\w+).*", "$1");
                keys = new ArrayList<>();
            } else if (t.matches("\\d+: (invoke\\w+|getstatic|iaload).*")) {
                String op = t.replaceFirst("^\\d+: (\\w+).*", "$1");
                String target = t.contains("// ") ? " " + t.substring(t.indexOf("// ") + 3).replaceFirst("^(Method|Field) ", "") : "";
                System.out.println("    " + op + target);
            }
        }
    }

    static String tool(String name, String... args) {
        StringWriter out = new StringWriter();
        int rc = ToolProvider.findFirst(name).orElseThrow().run(new PrintWriter(out), new PrintWriter(out), args);
        if (rc != 0) throw new IllegalStateException(name + " failed:\n" + out);
        return out.toString();
    }
}

/* Output:
"Aa".hashCode() = 2112, "BB".hashCode() = 2112, "cat".hashCode() = 98262
class files: [Color.class, Sw$1.class, Sw.class]
dense():
    tableswitch keys [1, 2, 3, 4, 5]
sparse():
    lookupswitch keys [1, 1000, 1000000]
text():
    invokevirtual java/lang/String.hashCode:()I
    lookupswitch keys [2112, 98262]
    invokevirtual java/lang/String.equals:(Ljava/lang/Object;)Z
    invokevirtual java/lang/String.equals:(Ljava/lang/Object;)Z
    invokevirtual java/lang/String.equals:(Ljava/lang/Object;)Z
    tableswitch keys [0, 1, 2]
color():
    getstatic Sw$1.$SwitchMap$Color:[I
    invokevirtual Color.ordinal:()I
    iaload
    lookupswitch keys [1, 2]
*/
