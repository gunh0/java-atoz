// javax.xml.transform (TrAX) turns a Source into a Result through a
// Transformer. With no stylesheet it is an "identity" transform, the
// standard way to serialize a DOM back to text; with an XSLT stylesheet it
// rewrites XML into other XML, HTML or plain text (here: CSV).
// Output properties control serialization: OMIT_XML_DECLARATION, INDENT,
// METHOD ("xml", "html", "text"), ENCODING. Indentation details differ
// between processors, so tests should not depend on pretty-printed output.
// Security: a stylesheet is a program. For untrusted XML or XSLT turn on
// FEATURE_SECURE_PROCESSING and block external DTDs and stylesheets with
// ACCESS_EXTERNAL_DTD / ACCESS_EXTERNAL_STYLESHEET set to "".
import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class TransformExample {
    static final String XSLT = """
            <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:output method="text"/>
              <xsl:template match="/">
                <xsl:text>host,port,tls&#10;</xsl:text>
                <xsl:for-each select="services/service">
                  <xsl:sort select="@port" data-type="number"/>
                  <xsl:value-of select="concat(@host, ',', @port, ',', @tls = 'true')"/>
                  <xsl:text>&#10;</xsl:text>
                </xsl:for-each>
              </xsl:template>
            </xsl:stylesheet>
            """;

    static TransformerFactory secureFactory() {
        TransformerFactory tf = TransformerFactory.newInstance();
        try {
            tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        // The default listener prints to stderr; this one stays quiet and lets errors surface as exceptions
        tf.setErrorListener(new ErrorListener() {
            public void warning(TransformerException e) { }
            public void error(TransformerException e) throws TransformerException { throw e; }
            public void fatalError(TransformerException e) throws TransformerException { throw e; }
        });
        return tf;
    }

    public static void main(String[] args) throws Exception {
        // Build a small DOM in code
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("services");
        doc.appendChild(root);
        String[][] rows = {{"api.internal", "8443", "true"}, {"cache", "6379", "false"}, {"db", "5432", "true"}};
        for (String[] r : rows) {
            Element s = doc.createElement("service");
            s.setAttribute("host", r[0]);
            s.setAttribute("port", r[1]);
            s.setAttribute("tls", r[2]);
            root.appendChild(s);
        }
        root.appendChild(doc.createComment(" generated "));

        TransformerFactory tf = secureFactory();

        // 1. Identity transform: DOM -> String
        Transformer identity = tf.newTransformer();
        identity.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        identity.setOutputProperty(OutputKeys.INDENT, "no");
        StringWriter xml = new StringWriter();
        identity.transform(new DOMSource(doc), new StreamResult(xml));
        System.out.println("serialized DOM:");
        System.out.println(xml);

        // 2. XSLT: the same data as CSV, sorted by port
        Transformer toCsv = tf.newTransformer(new StreamSource(new StringReader(XSLT)));
        StringWriter csv = new StringWriter();
        toCsv.transform(new StreamSource(new StringReader(xml.toString())), new StreamResult(csv));
        System.out.println("XSLT text output:");
        System.out.print(csv);

        // 3. Templates are the compiled, thread-safe form; Transformers are not
        var templates = tf.newTemplates(new StreamSource(new StringReader(XSLT)));
        StringWriter again = new StringWriter();
        templates.newTransformer().transform(new StreamSource(new StringReader(xml.toString())), new StreamResult(again));
        System.out.println("Templates gives the same result: " + again.toString().equals(csv.toString()));

        // 4. A stylesheet that pulls in an external document is refused
        String external = """
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:import href="http://example.invalid/evil.xsl"/>
                </xsl:stylesheet>
                """;
        try {
            tf.newTransformer(new StreamSource(new StringReader(external)));
            System.out.println("external stylesheet loaded (unexpected)");
        } catch (TransformerException e) {
            System.out.println("external stylesheet blocked: " + e.getClass().getSimpleName());
        }
    }
}

/* Output:
serialized DOM:
<services><service host="api.internal" port="8443" tls="true"/><service host="cache" port="6379" tls="false"/><service host="db" port="5432" tls="true"/><!-- generated --></services>
XSLT text output:
host,port,tls
db,5432,true
cache,6379,false
api.internal,8443,true
Templates gives the same result: true
external stylesheet blocked: TransformerConfigurationException
*/
