package no.rutebanken.marduk.geocoder.netex.kartverket;


import org.opengis.feature.Feature;
import org.rutebanken.netex.model.TopographicPlaceTypeEnumeration;

public class KommuneToTopographicPlace extends AbstractKartverketFeatureToTopographicPlace {

	public KommuneToTopographicPlace(Feature feature, String participantRef) {
		super(feature, participantRef);
	}

	@Override
	protected String getId() {
		return pad(getProperty("komm"), 4);
	}

	@Override
	protected String getParentId() {
		return getId().substring(0, 2);
	}

	@Override
	protected TopographicPlaceTypeEnumeration getType() {
		return TopographicPlaceTypeEnumeration.TOWN;
	}
}
