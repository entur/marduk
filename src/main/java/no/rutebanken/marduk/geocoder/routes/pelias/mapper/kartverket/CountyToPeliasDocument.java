package no.rutebanken.marduk.geocoder.routes.pelias.mapper.kartverket;

import no.rutebanken.marduk.geocoder.netex.TopographicPlaceAdapter;


public class CountyToPeliasDocument extends TopographicPlaceAdapterToPeliasDocument {


	public CountyToPeliasDocument(TopographicPlaceAdapter feature) {
		super(feature);
	}

	@Override
	protected String getLayer() {
		return "county";
	}

}
