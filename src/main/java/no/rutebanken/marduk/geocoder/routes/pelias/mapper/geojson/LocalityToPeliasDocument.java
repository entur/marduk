package no.rutebanken.marduk.geocoder.routes.pelias.mapper.geojson;


import no.rutebanken.marduk.geocoder.geojson.KartverketLocality;
import no.rutebanken.marduk.geocoder.routes.pelias.json.Parent;
import org.apache.commons.lang3.StringUtils;

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
				       .withCountyId(StringUtils.substring(getCountyId(), 0, 2))
				       .build();
	}
}
