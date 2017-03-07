package no.rutebanken.marduk.geocoder.routes.pelias.mapper.geojson;

import no.rutebanken.marduk.geocoder.geojson.KartverketCounty;


public class CountyToPeliasDocument extends KartverketFeatureToPeliasDocument {


	public CountyToPeliasDocument(KartverketCounty feature) {
		super(feature);
	}

	@Override
	protected String getLayer() {
		return "county";
	}

}
