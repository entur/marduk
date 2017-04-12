package no.rutebanken.marduk.geocoder.geojson;

import no.rutebanken.marduk.geocoder.netex.TopographicPlaceAdapter;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;


public class KartverketFeatureWrapperFactory {

	public static TopographicPlaceAdapter createWrapper(SimpleFeature feature) {
		Property typeProp = feature.getProperty("objtype");
		if (typeProp != null) {
			Object type = typeProp.getValue();
			if (KartverketCounty.OBJECT_TYPE.equals(type)) {
				return new KartverketCounty(feature);
			} else if (KartverketLocality.OBJECT_TYPE.equals(type)) {
				return new KartverketLocality(feature);
			} else if (KartverketBorough.OBJECT_TYPE.equals(type)) {
				return new KartverketBorough(feature);
			}
		} else {
			// TODO verify type
			return new KartverketNeighbourhood(feature);
		}

		throw new RuntimeException("Unable to map unsupported feature: " + feature);

	}
}
