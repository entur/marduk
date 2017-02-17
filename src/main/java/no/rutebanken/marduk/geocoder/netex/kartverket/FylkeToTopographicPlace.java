package no.rutebanken.marduk.geocoder.netex.kartverket;

import org.opengis.feature.Feature;
import org.rutebanken.netex.model.TopographicPlaceTypeEnumeration;

public class FylkeToTopographicPlace extends AbstractKartverketFeatureToTopographicPlace {

	public FylkeToTopographicPlace(Feature feature, String participantRef) {
		super(feature, participantRef);
	}

	@Override
	protected String getIsoCode() {
		return "NO-" + getId();
	}

	@Override
	protected String getId() {
		return pad(getProperty("fylkesnr"), 2);
	}

	@Override
	protected TopographicPlaceTypeEnumeration getType() {
		return TopographicPlaceTypeEnumeration.COUNTY;
	}
}
