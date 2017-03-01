package no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex;

import no.rutebanken.marduk.exceptions.FileValidationException;
import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import org.apache.commons.collections.CollectionUtils;
import org.rutebanken.netex.model.Common_VersionFrameStructure;
import org.rutebanken.netex.model.Place_VersionStructure;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.Site_VersionFrameStructure;
import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static javax.xml.bind.JAXBContext.newInstance;

@Service
public class DeliveryPublicationStreamToElasticsearchCommands {

	public Collection<ElasticsearchCommand> transform(InputStream publicationDeliveryStream) {
		try {
			PublicationDeliveryStructure deliveryStructure = unmarshall(publicationDeliveryStream);
			return fromDeliveryPublicationStructure(deliveryStructure);
		} catch (Exception e) {
			throw new FileValidationException("Parsing of DeliveryPublications failed: " + e.getMessage(), e);
		}
	}


	Collection<ElasticsearchCommand> fromDeliveryPublicationStructure(PublicationDeliveryStructure deliveryStructure) {
		List<ElasticsearchCommand> commands = new ArrayList<>();

		for (JAXBElement<? extends Common_VersionFrameStructure> frameStructureElmt : deliveryStructure.getDataObjects().getCompositeFrameOrCommonFrame()) {
			Common_VersionFrameStructure frameStructure = frameStructureElmt.getValue();
			if (frameStructure instanceof Site_VersionFrameStructure) {
				Site_VersionFrameStructure siteFrame = (Site_VersionFrameStructure) frameStructure;

				if (siteFrame.getStopPlaces() != null) {
					commands.addAll(addCommands(siteFrame.getStopPlaces().getStopPlace(), new StopPlaceToPeliasMapper(deliveryStructure.getParticipantRef())));
				}
				if (siteFrame.getStopPlaces() != null) {
					commands.addAll(addCommands(siteFrame.getTopographicPlaces().getTopographicPlace(), new TopographicPlaceToPeliasMapper(deliveryStructure.getParticipantRef())));
				}
			}
		}

		return commands;
	}

	private <P extends Place_VersionStructure> List<ElasticsearchCommand> addCommands(List<P> places, AbstractNetexPlaceToPeliasDocumentMapper<P> mapper) {
		if (!CollectionUtils.isEmpty(places)) {
			return places.stream().map(p -> ElasticsearchCommand.peliasIndexCommand(mapper.toPeliasDocument(p))).collect(Collectors.toList());
		}
		return new ArrayList<>();
	}

	private PublicationDeliveryStructure unmarshall(InputStream in) throws Exception {
		JAXBContext publicationDeliveryContext = newInstance(PublicationDeliveryStructure.class);
		Unmarshaller unmarshaller = publicationDeliveryContext.createUnmarshaller();
		JAXBElement<PublicationDeliveryStructure> jaxbElement = unmarshaller.unmarshal(new StreamSource(in), PublicationDeliveryStructure.class);
		return jaxbElement.getValue();
	}
}
