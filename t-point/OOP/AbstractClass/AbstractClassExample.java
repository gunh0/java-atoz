// An abstract class is a partially implemented class: it cannot be
// instantiated, may declare abstract methods (no body) that concrete
// subclasses must implement, and may also hold state, constructors and
// ordinary methods. It is the natural home for shared code plus "fill in the
// blanks" steps.
// Here the abstract class fixes the algorithm of a report export (a template
// method: header, rows, footer), and each subclass supplies only the format.
import java.util.List;

public class AbstractClassExample {
    public static void main(String[] args) {
        List<String[]> rows = List.of(
                new String[] {"alice", "admin"},
                new String[] {"bob", "viewer"});

        ReportExporter[] exporters = { new CsvExporter(), new MarkdownExporter() };
        for (ReportExporter exporter : exporters) {
            System.out.println("== " + exporter.formatName());
            System.out.print(exporter.export("users", rows));
        }

        // new ReportExporter() would not compile: the class is abstract
        System.out.println("rows exported in total: " + ReportExporter.exportedRows);
    }
}

abstract class ReportExporter {
    static int exportedRows;               // abstract classes can have state

    private final String name;

    protected ReportExporter(String name) { // and constructors, for subclasses
        this.name = name;
    }

    String formatName() {
        return name;
    }

    // The template method: final so subclasses cannot reorder the steps
    final String export(String title, List<String[]> rows) {
        StringBuilder out = new StringBuilder();
        out.append(header(title));
        for (String[] row : rows) {
            out.append(row(row));
            exportedRows++;
        }
        out.append(footer(rows.size()));
        return out.toString();
    }

    protected abstract String header(String title);

    protected abstract String row(String[] cells);

    // A default step that subclasses may override if they want
    protected String footer(int count) {
        return "";
    }
}

class CsvExporter extends ReportExporter {
    CsvExporter() {
        super("CSV");
    }

    @Override
    protected String header(String title) {
        return "name,role\n";
    }

    @Override
    protected String row(String[] cells) {
        return String.join(",", cells) + "\n";
    }
}

class MarkdownExporter extends ReportExporter {
    MarkdownExporter() {
        super("Markdown");
    }

    @Override
    protected String header(String title) {
        return "## " + title + "\n| name | role |\n|---|---|\n";
    }

    @Override
    protected String row(String[] cells) {
        return "| " + String.join(" | ", cells) + " |\n";
    }

    @Override
    protected String footer(int count) {
        return "\n" + count + " rows\n";
    }
}

/* Output:
== CSV
name,role
alice,admin
bob,viewer
== Markdown
## users
| name | role |
|---|---|
| alice | admin |
| bob | viewer |

2 rows
rows exported in total: 4
*/
