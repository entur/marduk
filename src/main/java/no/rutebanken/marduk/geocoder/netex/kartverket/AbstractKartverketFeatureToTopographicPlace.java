package no.rutebanken.marduk.geocoder.netex.kartverket;


import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Polygon;
import net.opengis.gml._3.AbstractRingPropertyType;
import net.opengis.gml._3.DirectPositionListType;
import net.opengis.gml._3.LinearRingType;
import net.opengis.gml._3.PolygonType;
import org.apache.commons.lang3.StringUtils;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.rutebanken.netex.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class AbstractKartverketFeatureToTopographicPlace {
	private final Logger logger = LoggerFactory.getLogger(getClass());

	protected Feature feature;

	protected String participantRef;

	private static Set<String> ids = new HashSet<>();

	public AbstractKartverketFeatureToTopographicPlace(Feature feature, String participantRef) {
		this.feature = feature;
		this.participantRef = participantRef;
	}


	public TopographicPlace toTopographicPlace() {
		return new TopographicPlace()
				       .withVersion("any").withModification(ModificationEnumeration.NEW)
				       .withName(multilingualString(getName()))
				       .withDescriptor(new TopographicPlaceDescriptor_VersionedChildStructure().withName(multilingualString(getName())))
				       .withTopographicPlaceType(getType())
				       .withPolygon(getPolygon())
				       .withIsoCode(getIsoCode())
				       .withCountryRef(new CountryRef().withRef(getCountryRef()))
				       .withId(prefix(getId()))
				       .withParentTopographicPlaceRef(new TopographicPlaceRefStructure()
						                                      .withRef(prefix(getParentId())));
	}


	protected String getName() {
		return getProperty("navn");
	}

	protected String prefix(String id) {
		return participantRef + ":TopographicPlace:" + id;
	}

	protected abstract String getId();

	protected abstract TopographicPlaceTypeEnumeration getType();


	protected String getIsoCode() {
		return null;
	}

	protected String pad(long val, int length) {
		return StringUtils.leftPad("" + val, length, "0");
	}

	protected String getParentId() {
		return null;
	}


	private PolygonType getPolygon() {
		Object geometry = feature.getDefaultGeometryProperty().getValue();

		if (geometry instanceof Polygon) {
			LinearRingType linearRing = new LinearRingType();

			Polygon polygon = (Polygon) geometry;

			List<Double> values = new ArrayList();
			for (Coordinate coordinate : polygon.getExteriorRing().getCoordinates()) {
				values.add(coordinate.x);
				values.add(coordinate.y);
			}

			// Ignoring interior rings because the corresponding exclaves are not handled.

			DirectPositionListType positionList = new DirectPositionListType().withValue(values);
			linearRing.withPosList(positionList);

			return new PolygonType().withId(participantRef + "-" + getId())
					       .withExterior(new AbstractRingPropertyType().withAbstractRing(
							       new net.opengis.gml._3.ObjectFactory().createLinearRing(linearRing)));
		}
		return null;
	}


	protected IanaCountryTldEnumeration getCountryRef() {
		return IanaCountryTldEnumeration.NO;
	}

	protected <T> T getProperty(String propertyName) {
		Property property = feature.getProperty(propertyName);
		if (property == null) {
			return null;
		}
		return (T) property.getValue();
	}

	protected MultilingualString multilingualString(String val) {
		return new MultilingualString().withLang("en").withValue(val);
	}

}


