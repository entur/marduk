package no.rutebanken.marduk.geocoder.netex.kartverket;

import org.opengis.feature.Feature;

public class KartverketFeatureMapper {


	public static AbstractKartverketFeatureToTopographicPlace create(Feature feature, String participantRef) {

		Object type = feature.getProperty("objtype").getValue();
		if ("Fylke".equals(type)) {
			return new FylkeToTopographicPlace(feature, participantRef);
		} else if ("Kommune".equals(type)) {
			return new KommuneToTopographicPlace(feature, participantRef);
		} else if ("Grunnkrets".equals(type)) {
			return new GrunnkretsToTopographicPlace(feature, participantRef);
		}
		throw new RuntimeException("Unable to map unsupported feature: " + feature);
	}

}
