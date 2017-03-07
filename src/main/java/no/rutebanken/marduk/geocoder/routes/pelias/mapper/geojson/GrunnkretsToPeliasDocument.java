package no.rutebanken.marduk.geocoder.routes.pelias.mapper.geojson;

import org.apache.commons.lang3.StringUtils;
import org.opengis.feature.simple.SimpleFeature;


public class GrunnkretsToPeliasDocument extends AbstractKartverketFeatureToPeliasDocument {


	public GrunnkretsToPeliasDocument(SimpleFeature feature) {
		super(feature);
	}

	@Override
	protected String getLayer() {
		return "borough";
	}

	@Override
	protected String getSourceId() {
		return "" + getProperty("grunnkrets");
	}

	@Override
	protected String getName() {
		return "" + getProperty("gkretsnavn");
	}

	@Override
	protected String getLocalityId() {
		return pad(getProperty("komm"), 4);
	}

	@Override
	protected String getCountyId() {
		return StringUtils.substring(getLocalityId(), 0, 2);
	}
}
