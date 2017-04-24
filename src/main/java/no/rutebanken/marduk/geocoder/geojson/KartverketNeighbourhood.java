package no.rutebanken.marduk.geocoder.geojson;


import org.apache.commons.lang3.StringUtils;
import org.opengis.feature.simple.SimpleFeature;

public class KartverketNeighbourhood extends AbstractKartverketGeojsonAdapter {

	public KartverketNeighbourhood(SimpleFeature feature) {
		super(feature);
	}

	@Override
	public String getId() {
		return "" + getProperty("enh_ssr_id");
	}

	@Override
	public String getName() {
		return getProperty("enh_snavn");
	}

	@Override
	public String getParentId() {
		return StringUtils.leftPad("" + getProperty("enh_komm"), 4, "0");
	}

	@Override
	public AbstractKartverketGeojsonAdapter.Type getType() {
		return Type.NEIGHBOURHOOD;
	}

}
