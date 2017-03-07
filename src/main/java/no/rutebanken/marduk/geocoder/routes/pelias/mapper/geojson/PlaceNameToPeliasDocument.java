package no.rutebanken.marduk.geocoder.routes.pelias.mapper.geojson;


import no.rutebanken.marduk.geocoder.routes.pelias.json.Parent;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import org.apache.commons.lang3.StringUtils;
import org.opengis.feature.simple.SimpleFeature;

public class PlaceNameToPeliasDocument extends AbstractKartverketFeatureToPeliasDocument {

	public PlaceNameToPeliasDocument(SimpleFeature simpleFeature) {
		super(simpleFeature);
	}

	@Override
	protected String getLayer() {
		return "neighbourhood";
	}

	@Override
	protected String getSourceId() {
		return "SSR-ID:" + getProperty("enh_ssr_id");
	}

	@Override
	protected String getName() {
		return getProperty("enh_snavn");
	}

	@Override
	protected String getLocalityId() {
		return  StringUtils.leftPad("" + getProperty("enh_komm"), 4, "0");
	}

	@Override
	protected String getCountyId() {
		return StringUtils.leftPad("" + getProperty("kom_fylkesnr"), 2, "0");
	}
}


