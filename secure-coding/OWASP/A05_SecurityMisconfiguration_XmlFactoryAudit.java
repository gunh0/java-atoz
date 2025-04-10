// OWASP Top 10 2021: A05:2021 Security Misconfiguration, XML parser defaults (CWE-611, CWE-16)
// https://owasp.org/Top10/A05_2021-Security_Misconfiguration/
//
// XXE moved into A05 in the 2021 list because it is a configuration problem:
// every JAXP factory ships with defaults that let a document pull in
// external content (an entity, a DTD, an imported stylesheet or schema),
// and each factory is switched off with a DIFFERENT knob. Hardening only
// DocumentBuilderFactory (see IDS17-J) leaves the SAX parser behind a
// SOAP endpoint, the StAX reader in an import job, the TransformerFactory
// that renders a report and the SchemaFactory that loads a partner's XSD.
// This program audits all five: each one gets input that references a
// temporary "server file", once with default settings and once hardened,
// and the table shows whether the file's content was loaded.
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

public class A05_SecurityMisconfiguration_XmlFactoryAudit {
    static final String SECRET = "db.password=hunter2";

    interface Probe {
        boolean loadsExternal(boolean hardened) throws Exception; // true = the secret came back
    }

    record Check(String factory, String vector, Probe probe) { }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("a05");
        Path secret = Files.writeString(dir.resolve("secret.txt"), SECRET);
        Path secretXml = Files.writeString(dir.resolve("secret.xml"), "<s>" + SECRET + "</s>");
        Path partnerXsd = Files.writeString(dir.resolve("partner.xsd"),
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'><xs:element name='leaked' type='xs:string'/></xs:schema>");
        try {
            String xxe = "<?xml version='1.0'?><!DOCTYPE r [<!ENTITY x SYSTEM '" + secret.toUri() + "'>]><r>&x;</r>";
            String xsl = "<xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>"
                    + "<xsl:template match='/'><out><xsl:value-of select=\"document('" + secretXml.toUri() + "')/s\"/></out></xsl:template>"
                    + "</xsl:stylesheet>";
            String xsd = "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>"
                    + "<xs:include schemaLocation='" + partnerXsd.toUri() + "'/></xs:schema>";

            List<Check> checks = new ArrayList<>();
            checks.add(new Check("DocumentBuilderFactory", "external entity", hardened -> {
                DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
                if (hardened) {
                    f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                    f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                    f.setExpandEntityReferences(false);
                }
                var builder = f.newDocumentBuilder();
                builder.setErrorHandler(new DefaultHandler()); // no default stderr messages
                return builder.parse(new InputSource(new StringReader(xxe))).getDocumentElement()
                        .getTextContent().contains(SECRET);
            }));
            checks.add(new Check("SAXParserFactory", "external entity", hardened -> {
                SAXParserFactory f = SAXParserFactory.newInstance();
                if (hardened) {
                    f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    f.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                }
                StringBuilder text = new StringBuilder();
                f.newSAXParser().parse(new InputSource(new StringReader(xxe)), new DefaultHandler() {
                    @Override
                    public void characters(char[] ch, int start, int length) {
                        text.append(ch, start, length);
                    }
                });
                return text.toString().contains(SECRET);
            }));
            checks.add(new Check("XMLInputFactory (StAX)", "external entity", hardened -> {
                XMLInputFactory f = XMLInputFactory.newInstance();
                if (hardened) {
                    f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
                    f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
                }
                XMLStreamReader r = f.createXMLStreamReader(new StringReader(xxe));
                StringBuilder text = new StringBuilder();
                while (r.hasNext()) {
                    if (r.next() == XMLStreamReader.CHARACTERS) text.append(r.getText());
                }
                return text.toString().contains(SECRET);
            }));
            checks.add(new Check("TransformerFactory", "document() in XSLT", hardened -> {
                TransformerFactory f = TransformerFactory.newInstance();
                f.setErrorListener(new QuietErrors());
                if (hardened) {
                    f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                    f.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
                }
                var t = f.newTransformer(new StreamSource(new StringReader(xsl)));
                t.setErrorListener(new QuietErrors());
                StringWriter out = new StringWriter();
                t.transform(new StreamSource(new StringReader("<in/>")), new StreamResult(out));
                return out.toString().contains(SECRET);
            }));
            checks.add(new Check("TransformerFactory", "external entity in input", hardened -> {
                TransformerFactory f = TransformerFactory.newInstance();
                f.setErrorListener(new QuietErrors());
                if (hardened) {
                    f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                    f.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
                }
                var t = f.newTransformer(); // identity: "just pretty-print the upload"
                t.setErrorListener(new QuietErrors());
                StringWriter out = new StringWriter();
                t.transform(new StreamSource(new StringReader(xxe)), new StreamResult(out));
                return out.toString().contains(SECRET);
            }));
            checks.add(new Check("SchemaFactory", "xs:include of a file", hardened -> {
                SchemaFactory f = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                f.setErrorHandler(new DefaultHandler() {
                    @Override
                    public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXParseException {
                        throw e;
                    }
                });
                if (hardened) {
                    f.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                    f.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                }
                var validator = f.newSchema(new StreamSource(new StringReader(xsd))).newValidator();
                // <leaked> is declared only in the included file: it validates iff the file was read
                validator.validate(new StreamSource(new StringReader("<leaked>x</leaked>")));
                return true;
            }));

            System.out.printf("%-24s %-26s %-10s %s%n", "factory", "vector", "default", "hardened");
            for (Check c : checks) {
                System.out.printf("%-24s %-26s %-10s %s%n", c.factory(), c.vector(),
                        run(c.probe(), false), run(c.probe(), true));
            }
        } finally {
            for (Path p : List.of(secret, secretXml, partnerXsd, dir)) Files.deleteIfExists(p);
        }
    }

    // "LOADED" = the file's content reached the application; exceptions mean the parser refused
    static String run(Probe probe, boolean hardened) {
        try {
            return probe.loadsExternal(hardened) ? "LOADED" : "not loaded";
        } catch (Exception e) {
            return "refused";
        }
    }

    // Swallow the TransformerFactory's default console warnings; the probe reports the outcome
    static final class QuietErrors implements javax.xml.transform.ErrorListener {
        public void warning(javax.xml.transform.TransformerException e) { }

        public void error(javax.xml.transform.TransformerException e) throws javax.xml.transform.TransformerException {
            throw e;
        }

        public void fatalError(javax.xml.transform.TransformerException e) throws javax.xml.transform.TransformerException {
            throw e;
        }
    }
}

/* Output:
factory                  vector                     default    hardened
DocumentBuilderFactory   external entity            LOADED     refused
SAXParserFactory         external entity            LOADED     refused
XMLInputFactory (StAX)   external entity            LOADED     refused
TransformerFactory       document() in XSLT         LOADED     refused
TransformerFactory       external entity in input   LOADED     refused
SchemaFactory            xs:include of a file       LOADED     refused
*/
