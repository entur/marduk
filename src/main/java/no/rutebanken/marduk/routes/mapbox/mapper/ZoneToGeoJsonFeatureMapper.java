package no.rutebanken.marduk.routes.mapbox.mapper;

import org.geojson.Feature;
import org.geojson.LngLatAlt;
import org.geojson.Point;
import org.rutebanken.netex.model.Zone_VersionStructure;
import org.springframework.stereotype.Service;

@Service
public class ZoneToGeoJsonFeatureMapper {

    public Feature mapZoneToGeoJson(Zone_VersionStructure zone) {

        Feature feature = new Feature();
        feature.setId(zone.getId());
        if(zone.getName() != null) {
            feature.setProperty("name", zone.getName().getValue());
        }

        if (zone.getCentroid() != null && zone.getCentroid().getLocation() != null) {
            double latitude = zone.getCentroid().getLocation().getLatitude().doubleValue();
            double longitude = zone.getCentroid().getLocation().getLongitude().doubleValue();

            LngLatAlt lngLatAlt = new LngLatAlt(longitude, latitude);
            Point multiPoint = new Point(lngLatAlt);
            feature.setGeometry(multiPoint);
        } else {
            throw new IllegalArgumentException("Cannot find centroid for Zone with ID: " + zone.getId());
        }

        return feature;

    }
}
