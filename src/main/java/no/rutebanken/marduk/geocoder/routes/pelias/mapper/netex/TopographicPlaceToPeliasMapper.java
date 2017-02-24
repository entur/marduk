package no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex;


import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import org.rutebanken.netex.model.TopographicPlace;

import java.util.Locale;

public class TopographicPlaceToPeliasMapper extends AbstractNetexPlaceToPeliasDocumentMapper<TopographicPlace> {


	public TopographicPlaceToPeliasMapper(String participantRef) {
		super(participantRef);
	}

	@Override
	protected void populateDocument(TopographicPlace place, PeliasDocument document) {
		document.setAlpha3(new Locale("en", place.getCountryRef().getRef().value()).getISO3Country());
	}

	@Override
	protected String getLayer(TopographicPlace place) {
		return place.getTopographicPlaceType().toString();
	}
}
