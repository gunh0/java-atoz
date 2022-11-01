// IDS17-J. Prevent XML External Entity Attacks
// https://wiki.sei.cmu.edu/confluence/display/java/IDS17-J.+Prevent+XML+External+Entity+Attacks
//
// XML lets a document declare entities in a DOCTYPE, including EXTERNAL
// entities whose replacement text is loaded from a URI. A parser with
// default settings resolves them, so an uploaded document such as
//   <!DOCTYPE r [<!ENTITY x SYSTEM "file:///.../secret.txt">]><r>&x;</r>
// makes the server read its own file and put the content into the parsed
// data, which is then echoed back, logged or stored (file disclosure, SSRF
// with http: URIs, denial of service). If you do not need DTDs, disallow
// them entirely; otherwise switch off external entities and external DTD
// access. Here the "server file" is a temporary file the program creates.
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

public class IDS17_XxeAttack {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("ids17");
        Path secret = Files.writeString(dir.resolve("secret.txt"), "db.password=hunter2");
        try {
            String benign = "<order><item>book</item></order>";
            String hostile = "<?xml version=\"1.0\"?>"
                    + "<!DOCTYPE order [<!ENTITY xxe SYSTEM \"" + secret.toUri() + "\">]>"
                    + "<order><item>&xxe;</item></order>";

            for (String[] input : new String[][] {{"benign", benign}, {"hostile", hostile}}) {
                System.out.println(input[0] + " document:");
                System.out.println("  noncompliant default parser:   " + parse(noncompliantFactory(), input[1]));
                System.out.println("  compliant, DOCTYPE disallowed: " + parse(compliantFactory(), input[1]));
                System.out.println("  compliant, no external access: " + parse(restrictedFactory(), input[1]));
            }
        } finally {
            Files.delete(secret);
            Files.delete(dir);
        }
    }

    // Noncompliant: default settings resolve external entities
    static DocumentBuilderFactory noncompliantFactory() {
        return DocumentBuilderFactory.newInstance();
    }

    // Compliant: refuse any DOCTYPE (the strongest option)
    static DocumentBuilderFactory compliantFactory() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        return dbf;
    }

    // Compliant when DTDs must be accepted: no external entities, no external DTD/schema access
    static DocumentBuilderFactory restrictedFactory() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return dbf;
    }

    static String parse(DocumentBuilderFactory dbf, String xml) {
        try {
            var builder = dbf.newDocumentBuilder();
            builder.setErrorHandler(null); // no default error printing to stderr
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            return "item = [" + doc.getElementsByTagName("item").item(0).getTextContent() + "]";
        } catch (Exception e) {
            return "rejected (" + e.getClass().getSimpleName() + ")";
        }
    }
}

/* Output:
benign document:
  noncompliant default parser:   item = [book]
  compliant, DOCTYPE disallowed: item = [book]
  compliant, no external access: item = [book]
hostile document:
  noncompliant default parser:   item = [db.password=hunter2]
  compliant, DOCTYPE disallowed: rejected (SAXParseException)
  compliant, no external access: item = []
*/
