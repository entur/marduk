package no.rutebanken.marduk.geocoder.netex;


import no.rutebanken.marduk.geocoder.netex.kartverket.GeoJsonTopographicPlaceReader;
import no.rutebanken.marduk.geocoder.netex.pbf.PbfTopographicPlaceReader;
import org.junit.Test;
import org.rutebanken.netex.model.IanaCountryTldEnumeration;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.validation.NeTExValidator;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import static javax.xml.bind.JAXBContext.newInstance;

public class TopographicPlaceConverterTest {


//	@Test
//	public void testConvertAdminUnitsFromGeoJson() throws Exception {
//		String targetPath = "files/adm-units.xml";
//		new TopographicPlaceConverter().toNetexFile(new GeoJsonTopographicPlaceReader
//				                                            (new File("conf/abas/fylker.geojson"),
//// TODO test with fylker?
//		new File("conf/abas/kommuner.geojson")
//				,
//				new File("conf/abas/grunnkretser.geojson")
//				                                            ),
//		targetPath);
//		validateNetexFile(targetPath);
//	}
//

	@Test
	public void testConvertPlaceOfInterestFromOsmPbf() throws Exception {
		List<String> filter = Arrays.asList("leisure=common", "naptan:indicator");
		TopographicPlaceReader reader = new PbfTopographicPlaceReader(filter, IanaCountryTldEnumeration.NO,
				                                                             new File("src/test/resources/no/rutebanken/marduk/geocoder/pbf/sample.pbf"));
		String targetPath = "files/poi.xml";
		new TopographicPlaceConverter().toNetexFile(reader,
				targetPath);

		validateNetexFile(targetPath);
	}

	private void validateNetexFile(String path) throws Exception {
		JAXBContext publicationDeliveryContext = newInstance(PublicationDeliveryStructure.class);
		Unmarshaller unmarshaller = publicationDeliveryContext.createUnmarshaller();

		NeTExValidator neTExValidator = new NeTExValidator();
		unmarshaller.setSchema(neTExValidator.getSchema());
		XMLStreamReader xmlReader = XMLInputFactory.newInstance().createXMLStreamReader(new FileInputStream(new File(path)));
		unmarshaller.unmarshal(xmlReader);
	}
}
