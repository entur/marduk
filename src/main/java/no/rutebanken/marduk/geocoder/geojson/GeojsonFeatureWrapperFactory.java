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
    private final List<String> neighbourhoodTypeBlackList;

    public GeojsonFeatureWrapperFactory(@Value("#{'${geocoder.neighbourhood.type.blacklist:140}'.split(',')}") List<String> neighbourhoodTypeBlackList) {
        this.neighbourhoodTypeBlackList = neighbourhoodTypeBlackList;
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
            // TODO verify type
            return new KartverketNeighbourhood(feature, neighbourhoodTypeBlackList);
        }

        throw new RuntimeException("Unable to map unsupported feature: " + feature);

    }

}
