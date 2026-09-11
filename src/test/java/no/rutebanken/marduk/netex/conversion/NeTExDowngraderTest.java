package no.rutebanken.marduk.netex.conversion;

import org.junit.jupiter.api.Test;
import no.rutebanken.marduk.netex.conversion.NeTExDowngrader.Conversion;
import org.rutebanken.netex.validation.NeTExValidator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXParseException;

import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static no.rutebanken.marduk.netex.conversion.ConversionTestSupport.NETEX_NS;
import static no.rutebanken.marduk.netex.conversion.ConversionTestSupport.childElementNames;
import static no.rutebanken.marduk.netex.conversion.ConversionTestSupport.isOnPath;
import static no.rutebanken.marduk.netex.conversion.ConversionTestSupport.netexXPath;
import static no.rutebanken.marduk.netex.conversion.ConversionTestSupport.parse;
import static no.rutebanken.marduk.netex.conversion.ConversionTestSupport.read;
import static no.rutebanken.marduk.netex.conversion.ConversionTestSupport.transformWithXmlstarlet;
import static no.rutebanken.marduk.netex.conversion.ConversionTestSupport.validate;

class NeTExDowngraderTest {

    private static final Path FIXTURE_1_16 = Paths.get("src/test/resources/no/rutebanken/marduk/netex/conversion/netex-1.16-dated-service-journey.xml");
    private static final Path STYLESHEET = Paths.get("src/main/resources/netex/conversion/netex-1.16-to-1.15.xsl");
    private static final String REPLACING_DSJ = "VYG:DatedServiceJourney:96_KMB-NK_23-12-09";
    private static final String REPLACED_DSJ = "VYG:DatedServiceJourney:8916_KVG-DEG_23-10-19";

    @Test
    void fixtureIsValid116ButInvalid115() throws Exception {
        String xml = readFixture();
        validate(NeTExValidator.NetexVersion.v1_16, xml);
        assertThatThrownBy(() -> validate(NeTExValidator.NetexVersion.v1_15, xml))
                .isInstanceOf(SAXParseException.class)
                .hasMessageContaining("replacedJourneys");
    }

    @Test
    void downgradedDocumentIsValid115() throws Exception {
        String downgraded = downgrade(readFixture());
        validate(NeTExValidator.NetexVersion.v1_15, downgraded);
    }

    @Test
    void downgradeRewritesDatedServiceJourney() throws Exception {
        Document document = parse(downgrade(readFixture()));
        XPath xpath = netexXPath();

        assertThat(xpath.evaluate("/n:PublicationDelivery/@version", document))
                .isEqualTo("1.15:NO-NeTEx-networktimetable:1.5");
        assertThat(((NodeList) xpath.evaluate("//n:replacedJourneys", document, XPathConstants.NODESET)).getLength())
                .isZero();

        Element replacing = (Element) xpath.evaluate("//n:DatedServiceJourney[@id='" + REPLACING_DSJ + "']", document, XPathConstants.NODE);
        assertThat(childElementNames(replacing))
                .containsExactly("ServiceAlteration", "ServiceJourneyRef", "DatedServiceJourneyRef", "OperatingDayRef");
        Element replacedRef = (Element) xpath.evaluate("n:DatedServiceJourneyRef", replacing, XPathConstants.NODE);
        assertThat(replacedRef.getNamespaceURI()).isEqualTo(NETEX_NS);
        assertThat(replacedRef.getAttribute("ref")).isEqualTo(REPLACED_DSJ);
        assertThat(replacedRef.getAttribute("version")).isEqualTo("1");

        // the replaced journey has no 1.16-only content and is copied unchanged
        Element replaced = (Element) xpath.evaluate("//n:DatedServiceJourney[@id='" + REPLACED_DSJ + "']", document, XPathConstants.NODE);
        assertThat(childElementNames(replaced)).containsExactly("ServiceJourneyRef", "OperatingDayRef");
    }

    @Test
    void uicOperatingPeriodIsKeptWhenThereIsNoOperatingDayRef() throws Exception {
        String xml = readFixture().replace("<OperatingDayRef ref=\"VYG:OperatingDay:2023-12-09\" version=\"1\"/>", "");
        String downgraded = downgrade(xml);
        validate(NeTExValidator.NetexVersion.v1_15, downgraded);

        Element replacing = (Element) netexXPath().evaluate("//n:DatedServiceJourney[@id='" + REPLACING_DSJ + "']", parse(downgraded), XPathConstants.NODE);
        assertThat(childElementNames(replacing))
                .containsExactly("ServiceAlteration", "ServiceJourneyRef", "DatedServiceJourneyRef", "UicOperatingPeriod");
    }

    @Test
    void bareVersionAttributeIsRewritten() throws Exception {
        String xml = "<PublicationDelivery xmlns=\"http://www.netex.org.uk/netex\" version=\"1.16\"/>";
        assertThat(netexXPath().evaluate("/n:PublicationDelivery/@version", parse(downgrade(xml)))).isEqualTo("1.15");
    }

    @Test
    void profileVersionIsRewrittenWhateverItWas() throws Exception {
        String xml = "<PublicationDelivery xmlns=\"http://www.netex.org.uk/netex\" version=\"1.16:NO-NeTEx-networktimetable:1.3\"/>";
        assertThat(netexXPath().evaluate("/n:PublicationDelivery/@version", parse(downgrade(xml))))
                .isEqualTo("1.15:NO-NeTEx-networktimetable:1.5");
    }

    @Test
    void versionWithoutProfileVersionKeepsTheProfileName() throws Exception {
        String xml = "<PublicationDelivery xmlns=\"http://www.netex.org.uk/netex\" version=\"1.16:NO-NeTEx-networktimetable\"/>";
        assertThat(netexXPath().evaluate("/n:PublicationDelivery/@version", parse(downgrade(xml))))
                .isEqualTo("1.15:NO-NeTEx-networktimetable");
    }

    @Test
    void patchVersionIsRewritten() throws Exception {
        String xml = "<PublicationDelivery xmlns=\"http://www.netex.org.uk/netex\" version=\"1.16.1:NO-NeTEx-networktimetable:1.6\"/>";
        assertThat(netexXPath().evaluate("/n:PublicationDelivery/@version", parse(downgrade(xml))))
                .isEqualTo("1.15:NO-NeTEx-networktimetable:1.5");
    }

    @Test
    void versionAtOrBeforeTheTargetVersionIsLeftAsIs() throws Exception {
        for (String version : List.of("1.15", "1.15:NO-NeTEx-networktimetable:1.5", "1.15.3:NO-NeTEx-networktimetable:1.5",
                "1.13:NO-NeTEx-networktimetable:1.3")) {
            String xml = "<PublicationDelivery xmlns=\"http://www.netex.org.uk/netex\" version=\"" + version + "\"/>";
            assertThat(netexXPath().evaluate("/n:PublicationDelivery/@version", parse(downgrade(xml)))).as(version).isEqualTo(version);
        }
    }

    @Test
    void indentationOfTheRewrittenElementIsPreserved() throws Exception {
        assertThat(downgrade(readFixture())).contains(
                "            <DatedServiceJourney version=\"1\" id=\"" + REPLACING_DSJ + "\">\n" +
                "              <ServiceAlteration>replaced</ServiceAlteration>\n" +
                "              <ServiceJourneyRef ref=\"VYG:ServiceJourney:96-KMB_87815-R\" version=\"11\"/>\n" +
                "              <DatedServiceJourneyRef ref=\"" + REPLACED_DSJ + "\" version=\"1\"/>\n" +
                "              <OperatingDayRef ref=\"VYG:OperatingDay:2023-12-09\" version=\"1\"/>\n" +
                "            </DatedServiceJourney>");
    }

    @Test
    void elementWrittenOnASingleLineStaysOnASingleLine() throws Exception {
        String datedServiceJourney = "<DatedServiceJourney version=\"1\" id=\"" + REPLACING_DSJ + "\">"
                + "<ServiceJourneyRef ref=\"VYG:ServiceJourney:96-KMB_87815-R\" version=\"11\"/>"
                + "<replacedJourneys><DatedVehicleJourneyRef ref=\"" + REPLACED_DSJ + "\" version=\"1\"/></replacedJourneys>"
                + "</DatedServiceJourney>";
        String xml = "<PublicationDelivery xmlns=\"http://www.netex.org.uk/netex\" version=\"1.16\">"
                + datedServiceJourney + "</PublicationDelivery>";

        assertThat(downgrade(xml)).contains("<DatedServiceJourney version=\"1\" id=\"" + REPLACING_DSJ + "\">"
                + "<ServiceJourneyRef ref=\"VYG:ServiceJourney:96-KMB_87815-R\" version=\"11\"/>"
                + "<DatedServiceJourneyRef ref=\"" + REPLACED_DSJ + "\" version=\"1\"/>"
                + "</DatedServiceJourney>");
    }

    @Test
    void documentsWithoutDatedServiceJourneyAreCopiedUnchanged() throws Exception {
        // version="any" carries no version number and is left alone
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<PublicationDelivery xmlns=\"http://www.netex.org.uk/netex\" version=\"any\">\n" +
                "    <PublicationTimestamp>2016-11-29T13:32:06.869+01:00</PublicationTimestamp>\n" +
                "    <ParticipantRef>NSR</ParticipantRef>\n" +
                "</PublicationDelivery>";
        String downgraded = downgrade(xml);
        validate(NeTExValidator.NetexVersion.v1_15, downgraded);
        assertThat(parse(downgraded).isEqualNode(parse(xml))).isTrue();

        String dateTimeExamples = read(Paths.get("src/test/resources/no/rutebanken/marduk/netex/conversion/date_time_examples.xml"));
        assertThat(parse(downgrade(dateTimeExamples)).isEqualNode(parse(dateTimeExamples))).isTrue();
    }

    @Test
    void downgraderIsCachedPerConversion() throws Exception {
        assertThat(NeTExDowngrader.getNeTExDowngrader()).isSameAs(NeTExDowngrader.getNeTExDowngrader(Conversion.V1_16_TO_V1_15));
        assertThat(NeTExDowngrader.getNeTExDowngrader().getConversion()).isEqualTo(NeTExDowngrader.LATEST);
        assertThat(NeTExDowngrader.LATEST.getSourceVersion()).isEqualTo(NetexVersion.v1_16);
        assertThat(NeTExDowngrader.LATEST.getTargetVersion()).isEqualTo(NetexVersion.v1_15);
    }

    /**
     * The stylesheet must stay XSLT 1.0 so that it can be applied with libxslt (xmlstarlet), independently of this library.
     */
    @Test
    void stylesheetWorksWithXmlstarlet() throws Exception {
        assumeTrue(isOnPath("xmlstarlet"), "xmlstarlet not installed");
        String output = transformWithXmlstarlet(STYLESHEET, FIXTURE_1_16);
        validate(NeTExValidator.NetexVersion.v1_15, output);
        assertThat(parse(output).isEqualNode(parse(downgrade(readFixture())))).as("xmlstarlet and JDK outputs differ").isTrue();
    }

    private static String readFixture() throws IOException {
        return read(FIXTURE_1_16);
    }

    private static String downgrade(String xml) throws Exception {
        StringWriter writer = new StringWriter();
        NeTExDowngrader.getNeTExDowngrader().downgrade(new StreamSource(new StringReader(xml)), new StreamResult(writer));
        return writer.toString();
    }
}
