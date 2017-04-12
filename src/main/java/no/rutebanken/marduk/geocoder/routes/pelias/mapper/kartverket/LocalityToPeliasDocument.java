package no.rutebanken.marduk.geocoder.routes.pelias.mapper.kartverket;


import no.rutebanken.marduk.geocoder.netex.TopographicPlaceAdapter;
import no.rutebanken.marduk.geocoder.routes.pelias.json.Parent;

public class LocalityToPeliasDocument extends TopographicPlaceAdapterToPeliasDocument {


	public LocalityToPeliasDocument(TopographicPlaceAdapter feature) {
		super(feature);
	}

	@Override
	protected String getLayer() {
		return "locality";
	}


	@Override
	protected Parent getParent() {
		return Parent.builder()
				       .withCountryId("NOR")
				       .withCountyId(feature.getParentId())
				       .build();
	}
}
