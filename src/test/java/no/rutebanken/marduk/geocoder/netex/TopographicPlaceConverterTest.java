package no.rutebanken.marduk.geocoder.netex;


import no.rutebanken.marduk.geocoder.netex.kartverket.GeoJsonTopographicPlaceReader;
import no.rutebanken.marduk.geocoder.netex.pbf.PbfTopographicPlaceReader;
import no.rutebanken.marduk.geocoder.featurejson.FeatureJSONFilter;
import no.rutebanken.marduk.geocoder.netex.sosi.SosiTopographicPlaceReader;
import org.junit.Test;
import org.rutebanken.netex.model.IanaCountryTldEnumeration;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.validation.NeTExValidator;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.List;

import static javax.xml.bind.JAXBContext.newInstance;

public class TopographicPlaceConverterTest {


    @Test
    public void testFilterConvertAdminUnitsFromGeoJson() throws Exception {
        String filteredFilePath = "target/filtered-fylker.geojson";
        new FeatureJSONFilter("src/test/resources/no/rutebanken/marduk/geocoder/geojson/fylker.geojson", filteredFilePath, "fylkesnr", "area").filter();

        String targetPath = "target/adm-units-from-geojson.xml";
        new TopographicPlaceConverter().toNetexFile(new GeoJsonTopographicPlaceReader
                                                            (new File(filteredFilePath)
                                                            ), targetPath);
        validateNetexFile(targetPath);
    }


    @Test
    public void testConvertPlaceOfInterestFromOsmPbf() throws Exception {
        List<String> filter = Arrays.asList("leisure=common", "naptan:indicator");
        TopographicPlaceReader reader = new PbfTopographicPlaceReader(filter, IanaCountryTldEnumeration.NO,
                                                                             new File("src/test/resources/no/rutebanken/marduk/geocoder/pbf/sample.pbf"));
        String targetPath = "target/poi.xml";
        new TopographicPlaceConverter().toNetexFile(reader,
                targetPath);

        validateNetexFile(targetPath);
    }

    @Test
    public void testConvertAdminUnitsFromSosi() throws Exception {
        TopographicPlaceReader reader = new SosiTopographicPlaceReader(new File("src/test/resources/no/rutebanken/marduk/geocoder/sosi/SosiTest.sos"));
        String targetPath = "target/admin-units-from-sosi.xml";
        new TopographicPlaceConverter().toNetexFile(reader,
                targetPath);

        validateNetexFile(targetPath);
    }


//    @Test // File is to big for source control
//    public void testConvertAdminUnitsFromSosiRealKartverketData() throws Exception {
//        TopographicPlaceReader reader = new SosiTopographicPlaceReader(new File("files/ADM_enheter_Norge.sos"));
//        String targetPath = "target/admin-units-from-sosi.xml";
//        new TopographicPlaceConverter().toNetexFile(reader,
//                targetPath);
//
//        validateNetexFile(targetPath);
//    }


    private void validateNetexFile(String path) throws Exception {
        JAXBContext publicationDeliveryContext = newInstance(PublicationDeliveryStructure.class);
        Unmarshaller unmarshaller = publicationDeliveryContext.createUnmarshaller();

        NeTExValidator neTExValidator = new NeTExValidator();
        unmarshaller.setSchema(neTExValidator.getSchema());
        XMLStreamReader xmlReader = XMLInputFactory.newInstance().createXMLStreamReader(new FileInputStream(new File(path)));
        unmarshaller.unmarshal(xmlReader);
    }


}
