package no.rutebanken.marduk.geocoder.geojson;


import org.opengis.feature.simple.SimpleFeature;

public class KartverketLocality extends AbstractKartverketGeojsonWrapper {

	public static final String OBJECT_TYPE="Kommune";

	public KartverketLocality(SimpleFeature feature) {
		super(feature);
	}

	@Override
	public String getId() {
		return pad(getProperty("komm"), 4);
	}

	@Override
	public String getParentId() {
		return getId().substring(0, 2);
	}

	@Override
	public AbstractKartverketGeojsonWrapper.Type getType() {
		return Type.LOCALITY;
	}

}


