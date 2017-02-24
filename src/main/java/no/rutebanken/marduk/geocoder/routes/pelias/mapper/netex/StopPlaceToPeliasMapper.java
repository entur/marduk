package no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex;


import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import org.rutebanken.netex.model.StopPlace;

public class StopPlaceToPeliasMapper extends AbstractNetexPlaceToPeliasDocumentMapper<StopPlace> {

	public StopPlaceToPeliasMapper(String participantRef) {
		super(participantRef);
	}

	@Override
	protected void populateDocument(StopPlace place, PeliasDocument document) {

	}

	@Override
	protected String getLayer(StopPlace place) {
		return "STOP_PLACE";
	}
}
