// Tiered compilation, observed with -XX:+PrintCompilation
//
// HotSpot starts every method in the interpreter (tier 0) and counts calls
// and loop back-edges. A warm method is compiled by C1, the fast client
// compiler, at tier 3: native code that still collects a profile (types seen,
// branch frequencies). When it gets hot, C2 recompiles it at tier 4 using
// that profile for speculative optimisations (inlining by observed receiver
// type, pruning never-taken branches), and the tier-3 code is "made not
// entrant". Tiers 1 and 2 are C1 without or with limited profiling, used when
// a method is trivial or C2 is busy. A long-running loop does not wait for
// the next call: on-stack replacement (OSR, marked "%") swaps compiled code
// into the running frame. If a speculation fails, the code deoptimizes back
// to the interpreter. -XX:+PrintCompilation logs all of it; the timestamps,
// compile ids and exact order vary per run, so the program below runs child
// JVMs under different flags and prints only the verdicts that are stable.
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JitTiers {
    static final String HOT = """
            public class Hot {
                static int compute(int x) { return (x * 31) ^ (x >>> 3); }
                static long batch(int base) {
                    long sum = 0;
                    for (int i = 0; i < 10_000; i++) sum += compute(base + i);
                    return sum;
                }
                public static void main(String[] args) {
                    // Keep the program busy for a second so the compilers finish even on a loaded machine
                    long sum = 0, deadline = System.nanoTime() + 1_000_000_000L;
                    for (int round = 0; round < 200 || System.nanoTime() < deadline; round++) sum += batch(round);
                    System.out.println(sum != 42);
                }
            }
            """;

    // "   48    8       3       Hot::compute (9 bytes)"  ->  compile id, flags, tier, method
    static final Pattern LINE = Pattern.compile("^\\s*\\d+\\s+\\d+\\s+([%sbn! ]*?)\\s*(\\d)?\\s+(Hot::\\w+)(.*)$");

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("jit");
        Path src = dir.resolve("Hot.java");
        Files.writeString(src, HOT);
        try {
            List<String> tiered = run(src);
            System.out.println("default (tiered):");
            Set<Integer> computeTiers = tiers(tiered, "Hot::compute");
            // tier 3 normally, tier 2 when C2's queue is long: which one varies between runs
            System.out.println("  compute compiled by C1 first:     " + (computeTiers.contains(2) || computeTiers.contains(3)));
            System.out.println("  then by C2 (tier 4):              " + computeTiers.contains(4));
            System.out.println("  C1 code made not entrant:         " + tiered.stream().anyMatch(l -> l.contains("Hot::compute") && l.contains("made not entrant")));
            System.out.println("  OSR compilation of batch's loop:  " + tiered.stream().anyMatch(l -> l.contains("%") && l.contains("Hot::batch @")));

            System.out.println("-XX:TieredStopAtLevel=1: tiers for compute " + tiers(run(src, "-XX:TieredStopAtLevel=1"), "Hot::compute"));
            List<String> c2only = run(src, "-XX:-TieredCompilation");
            System.out.println("-XX:-TieredCompilation:  compute compiled " + c2only.stream().anyMatch(l -> l.contains("Hot::compute"))
                    + ", with a tier number " + !tiers(c2only, "Hot::compute").isEmpty());
            System.out.println("-Xint:                   compute compiled " + run(src, "-Xint").stream().anyMatch(l -> l.contains("Hot::compute")));
        } finally {
            Files.delete(src);
            Files.delete(dir);
        }
    }

    static Set<Integer> tiers(List<String> lines, String method) {
        Set<Integer> tiers = new TreeSet<>();
        for (String l : lines) {
            Matcher m = LINE.matcher(l);
            if (m.matches() && m.group(3).equals(method) && m.group(2) != null) tiers.add(Integer.parseInt(m.group(2)));
        }
        return tiers;
    }

    static List<String> run(Path source, String... flags) throws Exception {
        List<String> cmd = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString()));
        cmd.addAll(List.of(flags));
        cmd.addAll(List.of("-XX:+PrintCompilation", source.toString()));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        List<String> lines = new String(p.getInputStream().readAllBytes()).lines().toList();
        if (p.waitFor() != 0) throw new IllegalStateException("child JVM failed: " + lines);
        return lines;
    }
}

/* Output:
default (tiered):
  compute compiled by C1 first:     true
  then by C2 (tier 4):              true
  C1 code made not entrant:         true
  OSR compilation of batch's loop:  true
-XX:TieredStopAtLevel=1: tiers for compute [1]
-XX:-TieredCompilation:  compute compiled true, with a tier number false
-Xint:                   compute compiled false
*/
