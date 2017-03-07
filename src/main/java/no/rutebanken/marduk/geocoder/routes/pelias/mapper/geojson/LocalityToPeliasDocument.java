package no.rutebanken.marduk.geocoder.routes.pelias.mapper.geojson;


import no.rutebanken.marduk.geocoder.geojson.KartverketLocality;
import no.rutebanken.marduk.geocoder.routes.pelias.json.Parent;

public class LocalityToPeliasDocument extends KartverketFeatureToPeliasDocument {


	public LocalityToPeliasDocument(KartverketLocality feature) {
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
				       .withCountyId(getCountyId().substring(0, 2))
				       .build();
	}
}
