package no.rutebanken.marduk.geocoder.routes.pelias.mapper.geojson;


import no.rutebanken.marduk.geocoder.routes.pelias.json.Parent;
import org.opengis.feature.simple.SimpleFeature;

public class KommuneToPeliasDocument extends AbstractKartverketFeatureToPeliasDocument {


	public KommuneToPeliasDocument(SimpleFeature feature) {
		super(feature);
	}

	@Override
	protected String getLayer() {
		return "locality";
	}

	@Override
	protected String getSourceId() {
		return pad(getProperty("komm"), 4);
	}

	@Override
	protected String getName() {
		return getProperty("navn");
	}

	@Override
	protected Parent getParent() {
		return Parent.builder()
				       .withCountryId("NOR")
				       .withCountyId(getSourceId().substring(0, 2))
				       .build();
	}
}
