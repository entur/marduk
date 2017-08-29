package no.rutebanken.marduk.geocoder.geojson;

import no.rutebanken.marduk.geocoder.netex.TopographicPlaceAdapter;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GeojsonFeatureWrapperFactory {

    // See relevant code values in http://www.kartverket.no/globalassets/standard/sosi-standarden-del-1-og-2/sosi-standarden/stedsnavn.pdf
    private final List<String> placeTypeWhiteList;

    // 101,102,103,104,105,106,107,132,228,266 = Plass/torg,by, bydel, tettsted, tettsteddel, bygd, grend, boligfelt, hyttefelt, industriområde (not ordered)
    public GeojsonFeatureWrapperFactory(@Value("#{'${geocoder.place.type.whitelist:101,102,103,104,105,107,132,228,266}'.split(',')}") List<String> placeTypeWhiteList) {
        this.placeTypeWhiteList = placeTypeWhiteList;
    }

    public TopographicPlaceAdapter createWrapper(SimpleFeature feature) {
        Property objTypeProp = feature.getProperty("objtype");
        Property whosOnFirstTypeProp = feature.getProperty("ne:type");
        if (objTypeProp != null) {
            Object type = objTypeProp.getValue();
            if (KartverketCounty.OBJECT_TYPE.equals(type)) {
                return new KartverketCounty(feature);
            } else if (KartverketLocality.OBJECT_TYPE.equals(type)) {
                return new KartverketLocality(feature);
            } else if (KartverketBorough.OBJECT_TYPE.equals(type)) {
                return new KartverketBorough(feature);
            }
        } else if (whosOnFirstTypeProp != null) {
            Object type = whosOnFirstTypeProp.getValue();
            if (WhosOnFirstCountry.TYPES.contains(type)) {
                return new WhosOnFirstCountry(feature);
            }
        } else {
            // Assuming remaining types are places
            return new KartverketPlace(feature, placeTypeWhiteList);
        }

        throw new RuntimeException("Unable to map unsupported feature: " + feature);

    }

}
