package no.rutebanken.marduk.geocoder.routes.pelias.mapper.geojson;


import no.rutebanken.marduk.geocoder.geojson.KartverketNeighbourhood;
import org.apache.commons.lang3.StringUtils;
import org.opengis.feature.simple.SimpleFeature;

public class NeighbourhoodToPeliasDocument extends KartverketFeatureToPeliasDocument {

	public NeighbourhoodToPeliasDocument(KartverketNeighbourhood simpleFeature) {
		super(simpleFeature);
	}

	@Override
	protected String getLayer() {
		return "neighbourhood";
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


