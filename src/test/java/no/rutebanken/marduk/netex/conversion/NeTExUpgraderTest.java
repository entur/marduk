package no.rutebanken.marduk.netex.conversion;

import org.junit.jupiter.api.Test;
import no.rutebanken.marduk.netex.conversion.NeTExUpgrader.Conversion;
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

class NeTExUpgraderTest {

    private static final Path FIXTURE_1_15 = Paths.get("src/test/resources/no/rutebanken/marduk/netex/conversion/netex-1.15-dated-service-journey.xml");
    private static final Path FIXTURE_1_16 = Paths.get("src/test/resources/no/rutebanken/marduk/netex/conversion/netex-1.16-dated-service-journey.xml");
    private static final Path STYLESHEET = Paths.get("src/main/resources/netex/conversion/netex-1.15-to-1.16.xsl");
    private static final String REPLACING_DSJ = "VYG:DatedServiceJourney:96_KMB-NK_23-12-09";
    private static final String FIRST_REPLACED_DSJ = "VYG:DatedServiceJourney:8916_KVG-DEG_23-10-19";
    private static final String SECOND_REPLACED_DSJ = "VYG:DatedServiceJourney:8917_KVG-DEG_23-10-20";

    @Test
    void fixtureIsValid115ButInvalid116() throws Exception {
        String xml = readFixture();
        validate(NeTExValidator.NetexVersion.v1_15, xml);
        assertThatThrownBy(() -> validate(NeTExValidator.NetexVersion.v1_16, xml))
                .isInstanceOf(SAXParseException.class)
                .hasMessageContaining("DatedServiceJourneyRef");
    }

    @Test
    void upgradedDocumentIsValid116() throws Exception {
        String upgraded = upgrade(readFixture());
        validate(NeTExValidator.NetexVersion.v1_16, upgraded);
    }

    @Test
    void upgradeRewritesDatedServiceJourney() throws Exception {
        Document document = parse(upgrade(readFixture()));
        XPath xpath = netexXPath();

        assertThat(xpath.evaluate("/n:PublicationDelivery/@version", document))
                .isEqualTo("1.16:NO-NeTEx-networktimetable:1.6");
        assertThat(((NodeList) xpath.evaluate("//n:DatedServiceJourneyRef", document, XPathConstants.NODESET)).getLength())
                .isZero();

        Element replacing = (Element) xpath.evaluate("//n:DatedServiceJourney[@id='" + REPLACING_DSJ + "']", document, XPathConstants.NODE);
        assertThat(childElementNames(replacing))
                .containsExactly("ServiceAlteration", "ServiceJourneyRef", "replacedJourneys", "OperatingDayRef");
        Element replacedJourneys = (Element) xpath.evaluate("n:replacedJourneys", replacing, XPathConstants.NODE);
        assertThat(replacedJourneys.getNamespaceURI()).isEqualTo(NETEX_NS);
        assertThat(childElementNames(replacedJourneys)).containsExactly("DatedVehicleJourneyRef", "DatedVehicleJourneyRef");
        NodeList replacedRefs = (NodeList) xpath.evaluate("n:DatedVehicleJourneyRef", replacedJourneys, XPathConstants.NODESET);
        Element firstReplacedRef = (Element) replacedRefs.item(0);
        assertThat(firstReplacedRef.getNamespaceURI()).isEqualTo(NETEX_NS);
        assertThat(firstReplacedRef.getAttribute("ref")).isEqualTo(FIRST_REPLACED_DSJ);
        assertThat(firstReplacedRef.getAttribute("version")).isEqualTo("1");
        assertThat(((Element) replacedRefs.item(1)).getAttribute("ref")).isEqualTo(SECOND_REPLACED_DSJ);

        // the replaced journeys have no 1.15-only content and are copied unchanged
        Element replaced = (Element) xpath.evaluate("//n:DatedServiceJourney[@id='" + FIRST_REPLACED_DSJ + "']", document, XPathConstants.NODE);
        assertThat(childElementNames(replaced)).containsExactly("ServiceJourneyRef", "OperatingDayRef");
    }

    /**
     * In 1.15 the journey references can appear in any order; in 1.16 replacedJourneys must follow the journey reference.
     */
    @Test
    void replacedJourneysFollowTheJourneyReferenceWhateverTheInputOrder() throws Exception {
        String serviceJourneyRef = "<ServiceJourneyRef ref=\"VYG:ServiceJourney:96-KMB_87815-R\" version=\"11\"/>\n";
        String firstReplacedRef = "<DatedServiceJourneyRef ref=\"" + FIRST_REPLACED_DSJ + "\" version=\"1\"/>\n";
        String xml = readFixture().replace(serviceJourneyRef + "              " + firstReplacedRef, firstReplacedRef + "              " + serviceJourneyRef);
        assertThat(xml).isNotEqualTo(readFixture());
        validate(NeTExValidator.NetexVersion.v1_15, xml);

        String upgraded = upgrade(xml);
        validate(NeTExValidator.NetexVersion.v1_16, upgraded);
        Element replacing = (Element) netexXPath().evaluate("//n:DatedServiceJourney[@id='" + REPLACING_DSJ + "']", parse(upgraded), XPathConstants.NODE);
        assertThat(childElementNames(replacing))
                .containsExactly("ServiceAlteration", "ServiceJourneyRef", "replacedJourneys", "OperatingDayRef");
    }

    @Test
    void uicOperatingPeriodIsKept() throws Exception {
        String xml = readFixture().replace("<OperatingDayRef ref=\"VYG:OperatingDay:2023-12-09\" version=\"1\"/>",
                "<UicOperatingPeriod version=\"1\" id=\"VYG:UicOperatingPeriod:1\">\n" +
                        "<FromDate>2023-12-09T00:00:00</FromDate>\n" +
                        "<ToDate>2023-12-09T00:00:00</ToDate>\n" +
                        "<ValidDayBits>1</ValidDayBits>\n" +
                        "</UicOperatingPeriod>");
        validate(NeTExValidator.NetexVersion.v1_15, xml);
        String upgraded = upgrade(xml);
        validate(NeTExValidator.NetexVersion.v1_16, upgraded);

        Element replacing = (Element) netexXPath().evaluate("//n:DatedServiceJourney[@id='" + REPLACING_DSJ + "']", parse(upgraded), XPathConstants.NODE);
        assertThat(childElementNames(replacing))
                .containsExactly("ServiceAlteration", "ServiceJourneyRef", "replacedJourneys", "UicOperatingPeriod");
    }

    @Test
    void bareVersionAttributeIsRewritten() throws Exception {
        String xml = "<PublicationDelivery xmlns=\"http://www.netex.org.uk/netex\" version=\"1.15\"/>";
        assertThat(netexXPath().evaluate("/n:PublicationDelivery/@version", parse(upgrade(xml)))).isEqualTo("1.16");
    }

    @Test
    void profileVersionIsRewrittenWhateverItWas() throws Exception {
        String xml = "<PublicationDelivery xmlns=\"http://www.netex.org.uk/netex\" version=\"1.15:NO-NeTEx-networktimetable:1.3\"/>";
        assertThat(netexXPath().evaluate("/n:PublicationDelivery/@version", parse(upgrade(xml))))
                .isEqualTo("1.16:NO-NeTEx-networktimetable:1.6");
    }

    @Test
    void versionWithoutProfileVersionKeepsTheProfileName() throws Exception {
        String xml = "<PublicationDelivery xmlns=\"http://www.netex.org.uk/netex\" version=\"1.15:NO-NeTEx-networktimetable\"/>";
        assertThat(netexXPath().evaluate("/n:PublicationDelivery/@version", parse(upgrade(xml))))
                .isEqualTo("1.16:NO-NeTEx-networktimetable");
    }

    /**
     * Exports still made against an older version of NeTEx and of the Nordic profile use the same
     * DatedServiceJourney as 1.15 and are upgraded the same way.
     */
    @Test
    void olderNetexVersionIsRewritten() throws Exception {
        String xml = readFixture().replace("version=\"1.15:NO-NeTEx-networktimetable:1.5\"",
                "version=\"1.13:NO-NeTEx-networktimetable:1.3\"");
        assertThat(xml).isNotEqualTo(readFixture());

        String upgraded = upgrade(xml);
        validate(NeTExValidator.NetexVersion.v1_16, upgraded);
        assertThat(netexXPath().evaluate("/n:PublicationDelivery/@version", parse(upgraded)))
                .isEqualTo("1.16:NO-NeTEx-networktimetable:1.6");
        assertThat(parse(upgraded).isEqualNode(parse(upgrade(readFixture())))).as("only the version differs").isTrue();
    }

    @Test
    void patchVersionIsRewritten() throws Exception {
        String xml = "<PublicationDelivery xmlns=\"http://www.netex.org.uk/netex\" version=\"1.15.3:NO-NeTEx-networktimetable:1.5\"/>";
        assertThat(netexXPath().evaluate("/n:PublicationDelivery/@version", parse(upgrade(xml))))
                .isEqualTo("1.16:NO-NeTEx-networktimetable:1.6");
    }

    @Test
    void versionAtOrAfterTheTargetVersionIsLeftAsIs() throws Exception {
        for (String version : List.of("1.16", "1.16:NO-NeTEx-networktimetable:1.6", "1.16.1:NO-NeTEx-networktimetable:1.6",
                "1.17:NO-NeTEx-networktimetable:1.7", "2.0")) {
            String xml = "<PublicationDelivery xmlns=\"http://www.netex.org.uk/netex\" version=\"" + version + "\"/>";
            assertThat(netexXPath().evaluate("/n:PublicationDelivery/@version", parse(upgrade(xml)))).as(version).isEqualTo(version);
        }
    }

    @Test
    void indentationOfTheRewrittenElementIsPreserved() throws Exception {
        assertThat(upgrade(readFixture())).contains(
                "            <DatedServiceJourney version=\"1\" id=\"" + REPLACING_DSJ + "\">\n" +
                "              <ServiceAlteration>replaced</ServiceAlteration>\n" +
                "              <ServiceJourneyRef ref=\"VYG:ServiceJourney:96-KMB_87815-R\" version=\"11\"/>\n" +
                "              <replacedJourneys>\n" +
                "                <DatedVehicleJourneyRef ref=\"" + FIRST_REPLACED_DSJ + "\" version=\"1\"/>\n" +
                "                <DatedVehicleJourneyRef ref=\"" + SECOND_REPLACED_DSJ + "\" version=\"1\"/>\n" +
                "              </replacedJourneys>\n" +
                "              <OperatingDayRef ref=\"VYG:OperatingDay:2023-12-09\" version=\"1\"/>\n" +
                "            </DatedServiceJourney>");
    }

    @Test
    void elementWrittenOnASingleLineStaysOnASingleLine() throws Exception {
        String datedServiceJourney = "<DatedServiceJourney version=\"1\" id=\"" + REPLACING_DSJ + "\">"
                + "<ServiceJourneyRef ref=\"VYG:ServiceJourney:96-KMB_87815-R\" version=\"11\"/>"
                + "<DatedServiceJourneyRef ref=\"" + FIRST_REPLACED_DSJ + "\" version=\"1\"/>"
                + "</DatedServiceJourney>";
        String xml = "<PublicationDelivery xmlns=\"http://www.netex.org.uk/netex\" version=\"1.15\">"
                + datedServiceJourney + "</PublicationDelivery>";

        assertThat(upgrade(xml)).contains("<DatedServiceJourney version=\"1\" id=\"" + REPLACING_DSJ + "\">"
                + "<ServiceJourneyRef ref=\"VYG:ServiceJourney:96-KMB_87815-R\" version=\"11\"/>"
                + "<replacedJourneys><DatedVehicleJourneyRef ref=\"" + FIRST_REPLACED_DSJ + "\" version=\"1\"/></replacedJourneys>"
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
        String upgraded = upgrade(xml);
        validate(NeTExValidator.NetexVersion.v1_16, upgraded);
        assertThat(parse(upgraded).isEqualNode(parse(xml))).isTrue();

        // a 1.07 document without DatedServiceJourney: only the version attribute is rewritten
        String dateTimeExamples = read(Paths.get("src/test/resources/no/rutebanken/marduk/netex/conversion/date_time_examples.xml"));
        String expected = dateTimeExamples.replace("version=\"1.07:NO-NeTEx-networktimetable:1.0\"",
                "version=\"1.16:NO-NeTEx-networktimetable:1.6\"");
        assertThat(expected).isNotEqualTo(dateTimeExamples);
        assertThat(parse(upgrade(dateTimeExamples)).isEqualNode(parse(expected))).isTrue();
    }

    /**
     * The upgrader is the inverse of the downgrader: converting back and forth gives the original document.
     */
    @Test
    void upgradeIsInverseOfDowngrade() throws Exception {
        String original115 = readFixture();
        assertThat(parse(downgrade(upgrade(original115))).isEqualNode(parse(original115))).isTrue();

        // the 1.16 fixture also carries a UicOperatingPeriod next to the OperatingDayRef, which the downgrader must drop
        String original116 = read(FIXTURE_1_16);
        String roundTripped116 = upgrade(downgrade(original116));
        validate(NeTExValidator.NetexVersion.v1_16, roundTripped116);
        String expected116 = original116.replaceAll("(?s)\\s*<UicOperatingPeriod.*?</UicOperatingPeriod>", "");
        assertThat(expected116).isNotEqualTo(original116);
        assertThat(parse(roundTripped116).isEqualNode(parse(expected116))).isTrue();
    }

    @Test
    void upgraderIsCachedPerConversion() throws Exception {
        assertThat(NeTExUpgrader.getNeTExUpgrader()).isSameAs(NeTExUpgrader.getNeTExUpgrader(Conversion.V1_15_TO_V1_16));
        assertThat(NeTExUpgrader.getNeTExUpgrader().getConversion()).isEqualTo(NeTExUpgrader.LATEST);
        assertThat(NeTExUpgrader.LATEST.getSourceVersion()).isEqualTo(NetexVersion.v1_15);
        assertThat(NeTExUpgrader.LATEST.getTargetVersion()).isEqualTo(NetexVersion.v1_16);
    }

    /**
     * The stylesheet must stay XSLT 1.0 so that it can be applied with libxslt (xmlstarlet), independently of this library.
     */
    @Test
    void stylesheetWorksWithXmlstarlet() throws Exception {
        assumeTrue(isOnPath("xmlstarlet"), "xmlstarlet not installed");
        String output = transformWithXmlstarlet(STYLESHEET, FIXTURE_1_15);
        validate(NeTExValidator.NetexVersion.v1_16, output);
        assertThat(parse(output).isEqualNode(parse(upgrade(readFixture())))).as("xmlstarlet and JDK outputs differ").isTrue();
    }

    private static String readFixture() throws IOException {
        return read(FIXTURE_1_15);
    }

    private static String upgrade(String xml) throws Exception {
        StringWriter writer = new StringWriter();
        NeTExUpgrader.getNeTExUpgrader().upgrade(new StreamSource(new StringReader(xml)), new StreamResult(writer));
        return writer.toString();
    }

    private static String downgrade(String xml) throws Exception {
        StringWriter writer = new StringWriter();
        NeTExDowngrader.getNeTExDowngrader().downgrade(new StreamSource(new StringReader(xml)), new StreamResult(writer));
        return writer.toString();
    }
}
