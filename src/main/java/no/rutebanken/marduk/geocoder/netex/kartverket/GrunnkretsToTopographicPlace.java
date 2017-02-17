package no.rutebanken.marduk.geocoder.netex.kartverket;


import org.opengis.feature.Feature;
import org.rutebanken.netex.model.TopographicPlaceTypeEnumeration;

public class GrunnkretsToTopographicPlace extends AbstractKartverketFeatureToTopographicPlace {


	public GrunnkretsToTopographicPlace(Feature feature, String participantRef) {
		super(feature, participantRef);
	}

	@Override
	protected String getName() {
		return getProperty("gkretsnavn");
	}

	@Override
	protected String getId() {
		return pad(getProperty("grunnkrets"), 8);
	}

	@Override
	protected String getParentId() {
		return pad(getProperty("komm"), 4);
	}

	@Override
	protected TopographicPlaceTypeEnumeration getType() {
		return TopographicPlaceTypeEnumeration.AREA;
	}
}
