package no.rutebanken.marduk.geocoder.routes.pelias.mapper.geojson;

import no.rutebanken.marduk.geocoder.geojson.KartverketBorough;
import org.apache.commons.lang3.StringUtils;
import org.opengis.feature.simple.SimpleFeature;


public class BoroughToPeliasDocument extends KartverketFeatureToPeliasDocument {


	public BoroughToPeliasDocument(KartverketBorough feature) {
		super(feature);
	}

	@Override
	protected String getLayer() {
		return "borough";
	}


	@Override
	protected String getLocalityId() {
		return feature.getParentId();
	}

	@Override
	protected String getCountyId() {
		return StringUtils.substring(getLocalityId(), 0, 2);
	}
}
