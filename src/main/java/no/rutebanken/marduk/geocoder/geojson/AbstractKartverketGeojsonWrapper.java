package no.rutebanken.marduk.geocoder.geojson;


import com.vividsolutions.jts.geom.Geometry;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceAdapter;
import org.apache.commons.lang3.StringUtils;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;

public abstract class AbstractKartverketGeojsonWrapper implements TopographicPlaceAdapter {

	protected SimpleFeature feature;

	public AbstractKartverketGeojsonWrapper(SimpleFeature feature) {
		this.feature = feature;
	}

	public String getIsoCode() {
		return null;
	}

	public String getParentId() {
		return null;
	}

	public String getName() {
		return getProperty("navn");
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


	protected String pad(long val, int length) {
		return StringUtils.leftPad("" + val, length, "0");
	}

}
