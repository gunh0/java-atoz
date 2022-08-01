// Builder (creational): separate the construction of a complex object from
// its representation, so the same construction process can create different
// representations.
//
// This is the GoF form of the pattern: a Director knows the STEPS (title,
// sections, findings, ...) and drives an abstract Builder; each concrete
// builder decides what those steps produce. One security assessment is turned
// into a Markdown report and into a plain-text summary by swapping builders.
// (effective/02_Builder.java shows the other, fluent "many optional
// parameters" builder.) JDK examples: StringBuilder is the builder of a String,
// and java.net.http.HttpRequest.Builder assembles a request step by step.
import java.util.List;

public class BuilderDemo {
    record Finding(String id, String severity, String title) {}

    // The builder interface: the construction steps
    interface ReportBuilder {
        void title(String text);
        void section(String heading);
        void finding(Finding f);
        void paragraph(String text);
    }

    // Concrete builder 1: a full Markdown document
    static final class MarkdownBuilder implements ReportBuilder {
        private final StringBuilder md = new StringBuilder();
        public void title(String text) { md.append("# ").append(text).append("\n"); }
        public void section(String heading) { md.append("\n## ").append(heading).append("\n"); }
        public void finding(Finding f) {
            md.append("- **").append(f.severity()).append("** `").append(f.id()).append("` ")
              .append(f.title()).append("\n");
        }
        public void paragraph(String text) { md.append(text).append("\n"); }
        String getResult() { return md.toString(); }
    }

    // Concrete builder 2: a one-line summary that ignores most of the steps
    static final class SummaryBuilder implements ReportBuilder {
        private String title = "";
        private int total;
        private int high;
        public void title(String text) { title = text; }
        public void section(String heading) { /* not part of a summary */ }
        public void finding(Finding f) {
            total++;
            if (f.severity().equals("HIGH")) high++;
        }
        public void paragraph(String text) { /* not part of a summary */ }
        String getResult() { return title + ": " + total + " findings, " + high + " high"; }
    }

    // The director: owns the order of steps, knows nothing about the output
    static final class AssessmentDirector {
        void construct(ReportBuilder b, String system, List<Finding> findings) {
            b.title("Security assessment: " + system);
            b.section("Scope");
            b.paragraph("External API and admin console, tested from an unauthenticated position.");
            b.section("Findings");
            findings.forEach(b::finding);
            b.section("Next steps");
            b.paragraph("Fix HIGH findings before the next release and re-test.");
        }
    }

    public static void main(String[] args) {
        List<Finding> findings = List.of(
                new Finding("F-01", "HIGH", "SQL injection in /search"),
                new Finding("F-02", "MEDIUM", "Missing rate limit on login"),
                new Finding("F-03", "HIGH", "Admin console exposed without MFA"));
        AssessmentDirector director = new AssessmentDirector();

        MarkdownBuilder markdown = new MarkdownBuilder();
        director.construct(markdown, "billing-service", findings);
        System.out.print(markdown.getResult());

        SummaryBuilder summary = new SummaryBuilder();
        director.construct(summary, "billing-service", findings);
        System.out.println();
        System.out.println(summary.getResult());
    }
}

/* Output:
# Security assessment: billing-service

## Scope
External API and admin console, tested from an unauthenticated position.

## Findings
- **HIGH** `F-01` SQL injection in /search
- **MEDIUM** `F-02` Missing rate limit on login
- **HIGH** `F-03` Admin console exposed without MFA

## Next steps
Fix HIGH findings before the next release and re-test.

Security assessment: billing-service: 3 findings, 2 high
*/
