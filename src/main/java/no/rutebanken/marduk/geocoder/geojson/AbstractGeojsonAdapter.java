package no.rutebanken.marduk.geocoder.geojson;

import com.vividsolutions.jts.geom.Geometry;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;

public abstract class AbstractGeojsonAdapter {

    protected SimpleFeature feature;

    public AbstractGeojsonAdapter(SimpleFeature feature) {
        this.feature = feature;
    }

    public Geometry getDefaultGeometry() {
        if (feature.getDefaultGeometryProperty() != null) {
            Object geometry = feature.getDefaultGeometryProperty().getValue();
            if (geometry instanceof Geometry) {
                return (Geometry) geometry;
            }
        }
        return null;
    }


    public <T> T getProperty(String propertyName) {
        Property property = feature.getProperty(propertyName);
        if (property == null) {
            return null;
        }
        return (T) property.getValue();
    }
}
