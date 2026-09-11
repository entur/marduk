package no.rutebanken.marduk.netex.conversion;

import org.rutebanken.netex.validation.NeTExValidator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Helpers shared by the converter tests.
 */
final class ConversionTestSupport {

    static final String NETEX_NS = "http://www.netex.org.uk/netex";

    private ConversionTestSupport() {
    }

    static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    static void validate(NeTExValidator.NetexVersion version, String xml) throws Exception {
        NeTExValidator.getNeTExValidator(version).validate(new StreamSource(new StringReader(xml)));
    }

    /**
     * Apply the stylesheet to the input with libxslt (xmlstarlet tr), asserting that the process succeeds.
     */
    static String transformWithXmlstarlet(Path stylesheet, Path input) throws Exception {
        Process process = new ProcessBuilder("xmlstarlet", "tr", stylesheet.toString(), input.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        return output;
    }

    static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setIgnoringComments(true);
        factory.setIgnoringElementContentWhitespace(true);
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        stripWhitespaceTextNodes(document.getDocumentElement());
        return document;
    }

    /** Element content whitespace is not stripped without a DTD, so do it by hand to make DOM comparison formatting-agnostic. */
    private static void stripWhitespaceTextNodes(Node node) {
        Node child = node.getFirstChild();
        while (child != null) {
            Node next = child.getNextSibling();
            if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().trim().isEmpty()) {
                node.removeChild(child);
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                stripWhitespaceTextNodes(child);
            }
            child = next;
        }
    }

    static List<String> childElementNames(Element element) {
        List<String> names = new ArrayList<>();
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                names.add(child.getLocalName());
            }
        }
        return names;
    }

    /**
     * @return an XPath evaluator with the prefix {@code n} bound to the NeTEx namespace.
     */
    static XPath netexXPath() {
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                return "n".equals(prefix) ? NETEX_NS : null;
            }

            @Override
            public String getPrefix(String namespaceURI) {
                return null;
            }

            @Override
            public Iterator<String> getPrefixes(String namespaceURI) {
                return null;
            }
        });
        return xpath;
    }

    static boolean isOnPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : path.split(File.pathSeparator)) {
            if (Files.isExecutable(Paths.get(dir, executable))) {
                return true;
            }
        }
        return false;
    }
}
