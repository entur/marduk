package no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex;

import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.validation.NeTExValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;

import static javax.xml.bind.JAXBContext.newInstance;

@Service
public class DeliveryPublicationToElasticsearchCommands {

	private final Logger logger = LoggerFactory.getLogger(getClass());


	public Collection<ElasticsearchCommand> transform(InputStream publicationDeliveryStream) {
//		try {
//			Object o = parse(publicationDeliveryStream);
//		} catch (Exception e) {
//
//		}
		return new ArrayList<>();
	}


	private Object parse(InputStream in) throws Exception {
		JAXBContext publicationDeliveryContext = newInstance(PublicationDeliveryStructure.class);
		Unmarshaller unmarshaller = publicationDeliveryContext.createUnmarshaller();

		NeTExValidator neTExValidator = new NeTExValidator();
		unmarshaller.setSchema(neTExValidator.getSchema());
		XMLStreamReader xmlReader = XMLInputFactory.newInstance().createXMLStreamReader(in);
		return unmarshaller.unmarshal(xmlReader);
	}
}
