package no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex;


import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import org.rutebanken.netex.model.StopPlace;

import java.util.Arrays;

public class StopPlaceToPeliasMapper extends AbstractNetexPlaceToPeliasDocumentMapper<StopPlace> {

	// Using region as substitute layer for stops to avoid having to fork pelias (custom layers not configurable).
	public static final String STOP_PLACE_LAYER = "region";

	public StopPlaceToPeliasMapper(String participantRef) {
		super(participantRef);
	}

	@Override
	protected void populateDocument(StopPlace place, PeliasDocument document) {
		if (place.getStopPlaceType() != null) {
			document.setCategory(Arrays.asList(place.getStopPlaceType().value()));
		}

	}

	@Override
	protected String getLayer(StopPlace place) {
		return STOP_PLACE_LAYER;
	}
}
