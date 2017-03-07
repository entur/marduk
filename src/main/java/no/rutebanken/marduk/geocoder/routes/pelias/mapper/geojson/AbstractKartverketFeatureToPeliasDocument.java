package no.rutebanken.marduk.geocoder.routes.pelias.mapper.geojson;


import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;
import no.rutebanken.marduk.geocoder.routes.pelias.json.GeoPoint;
import no.rutebanken.marduk.geocoder.routes.pelias.json.Parent;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import org.apache.commons.lang3.StringUtils;
import org.geojson.LngLatAlt;
import org.geojson.Polygon;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractKartverketFeatureToPeliasDocument {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public static final String SOURCE = "kartverket";

	private SimpleFeature feature;

	public AbstractKartverketFeatureToPeliasDocument(SimpleFeature feature) {
		this.feature = feature;
	}


	public PeliasDocument toPeliasDocument() {
		PeliasDocument document = new PeliasDocument(getLayer(), SOURCE, getSourceId());

		document.setDefaultName(getName());
		document.setCenterPoint(mapCenterPoint());
		document.setShape(mapShape());

		document.setParent(getParent());
		return document;
	}

	protected abstract String getLayer();

	protected abstract String getSourceId();

	protected abstract String getName();

	protected String getLocalityId() {
		return null;
	}

	protected String getCountyId() {
		return null;
	}

	protected Parent getParent() {
		return Parent.builder()
				       .withCountryId("NOR")
				       .withlocalityId(getLocalityId())
				       .withCountyId(getCountyId())
				       .build();
	}

	protected GeoPoint mapCenterPoint() {
		Object geometry = feature.getDefaultGeometryProperty().getValue();

		if (geometry instanceof Geometry) {
			Geometry jtsGeometry = (Geometry) geometry;

			if (jtsGeometry.isValid()) {
				Point centroid = jtsGeometry.getCentroid();
				return new GeoPoint(centroid.getY(), centroid.getX());
			}
		}

		return null;
	}

	protected Polygon mapShape() {
		Object geometry = feature.getDefaultGeometryProperty().getValue();

		if (geometry instanceof com.vividsolutions.jts.geom.Polygon) {
			com.vividsolutions.jts.geom.Polygon jtsPolygon = (com.vividsolutions.jts.geom.Polygon) geometry;

			if (jtsPolygon.isValid()) {

				List<LngLatAlt> coord = Arrays.stream(jtsPolygon.getExteriorRing().getCoordinates()).map(c -> new LngLatAlt(c.x, c.y)).collect(Collectors.toList());
				return new Polygon(removeConsecutiveDuplicates(coord));
			} else {
				logger.warn("Ignoring polygon for kartverket feature with invalid geometry: " + getLayer() + ":" + getName());
			}
		}
		return null;
	}

	private List<LngLatAlt> removeConsecutiveDuplicates(List<LngLatAlt> org) {
		LngLatAlt prev = null;
		List<LngLatAlt> withoutDuplicates = new ArrayList<>();
		for (LngLatAlt coord : org) {
			if (prev == null || !equals(coord, prev)) {
				withoutDuplicates.add(coord);
			}
			prev = coord;
		}
		return withoutDuplicates;
	}


	private boolean equals(LngLatAlt coordinate, LngLatAlt other) {
		return other.getLatitude() == coordinate.getLatitude() && other.getLongitude() == coordinate.getLongitude();
	}

	protected <T> T getProperty(String propertyName) {
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

