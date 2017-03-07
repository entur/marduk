package no.rutebanken.marduk.geocoder.routes.pelias.mapper.geojson;

import org.opengis.feature.simple.SimpleFeature;


public class FylkeToPeliasDocument extends AbstractKartverketFeatureToPeliasDocument {


	public FylkeToPeliasDocument(SimpleFeature feature) {
		super(feature);
	}

	@Override
	protected String getLayer() {
		return "county";
	}

	@Override
	protected String getSourceId() {
		return pad(getProperty("fylkesnr"), 2);
	}

	@Override
	protected String getName() {
		return getProperty("navn");
	}

}
