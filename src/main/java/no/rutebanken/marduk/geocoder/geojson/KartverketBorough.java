package no.rutebanken.marduk.geocoder.geojson;


import org.opengis.feature.simple.SimpleFeature;

public class KartverketBorough extends AbstractKartverketGeojsonAdapter {

	public static final String OBJECT_TYPE="Grunnkrets";

	@Override
	public String getName() {
		return getProperty("gkretsnavn");
	}

	@Override
	public String getId() {
		return pad(getProperty("grunnkrets"), 8);
	}

	@Override
	public String getParentId() {
		return pad(getProperty("komm"), 4);
	}

	@Override
	public AbstractKartverketGeojsonAdapter.Type getType() {
		return Type.BOROUGH;
	}

	public KartverketBorough(SimpleFeature feature) {
		super(feature);
	}


}
