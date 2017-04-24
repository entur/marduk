package no.rutebanken.marduk.geocoder.geojson;


import net.logstash.logback.encoder.org.apache.commons.lang.StringUtils;
import org.opengis.feature.simple.SimpleFeature;

public class KartverketLocality extends AbstractKartverketGeojsonAdapter {

	public static final String OBJECT_TYPE = "Kommune";

	public KartverketLocality(SimpleFeature feature) {
		super(feature);
	}

	@Override
	public String getId() {
		return pad(getProperty("komm"), 4);
	}

	@Override
	public String getParentId() {
		return StringUtils.substring(getId(), 0, 2);
	}

	@Override
	public AbstractKartverketGeojsonAdapter.Type getType() {
		return Type.LOCALITY;
	}

}


