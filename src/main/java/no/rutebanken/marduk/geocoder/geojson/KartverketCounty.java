package no.rutebanken.marduk.geocoder.geojson;


import org.opengis.feature.simple.SimpleFeature;

public class KartverketCounty extends AbstractKartverketGeojsonAdapter {

	public static final String OBJECT_TYPE="Fylke";

	public KartverketCounty(SimpleFeature feature) {
		super(feature);
	}

	@Override
	public String getIsoCode() {
		return "NO-" + getId();
	}

	@Override
	public String getId() {
		return pad(getProperty("fylkesnr"), 2);
	}

	@Override
	public AbstractKartverketGeojsonAdapter.Type getType() {
		return Type.COUNTY;
	}

}
