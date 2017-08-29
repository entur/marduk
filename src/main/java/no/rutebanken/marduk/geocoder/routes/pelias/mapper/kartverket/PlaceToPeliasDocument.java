package no.rutebanken.marduk.geocoder.routes.pelias.mapper.kartverket;


import no.rutebanken.marduk.geocoder.netex.TopographicPlaceAdapter;
import org.apache.commons.lang3.StringUtils;

public class PlaceToPeliasDocument extends TopographicPlaceAdapterToPeliasDocument {

	public PlaceToPeliasDocument(TopographicPlaceAdapter simpleFeature) {
		super(simpleFeature);
	}

	@Override
	protected String getLayer() {
		return "address";
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


