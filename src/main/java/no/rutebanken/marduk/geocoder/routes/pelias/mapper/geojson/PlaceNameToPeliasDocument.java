package no.rutebanken.marduk.geocoder.routes.pelias.mapper.geojson;


import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;
import no.rutebanken.marduk.geocoder.routes.pelias.json.GeoPoint;
import no.rutebanken.marduk.geocoder.routes.pelias.json.Parent;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;

import org.apache.commons.lang3.StringUtils;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;

public class PlaceNameToPeliasDocument {

	private static final String SOURCE = "Kartverket";

	private SimpleFeature feature;

	public PlaceNameToPeliasDocument(SimpleFeature simpleFeature) {
		this.feature = simpleFeature;
	}

	public PeliasDocument toPeliasDocument() {
		PeliasDocument document = new PeliasDocument("neighbourhood", SOURCE, "SSR-ID:" + getProperty("enh_ssr_id"));

		document.setDefaultName(getProperty("enh_snavn"));
		document.setCenterPoint(mapCenterPoint());

		document.setParent(toParent());
		return document;
	}

	private Parent toParent() {
		return Parent.builder()
				       .withCountryId(SOURCE + ":country:NOR")
				       .withLocaladminId(SOURCE + ":localadmin:" + StringUtils.leftPad("" + getProperty("enh_komm"), 4, "0"))
				       .withCountyId(SOURCE + ":county:" + StringUtils.leftPad("" + getProperty("kom_fylkesnr"), 2, "0"))
				       .build();
	}

	private GeoPoint mapCenterPoint() {
		Object geometry = feature.getDefaultGeometryProperty().getValue();

		if (geometry instanceof Geometry) {
			Point centroid = ((Geometry) geometry).getCentroid();

			return new GeoPoint(centroid.getY(), centroid.getX());
		}
		return null;
	}

	protected <T> T getProperty(String propertyName) {
		Property property = feature.getProperty(propertyName);
		if (property == null) {
			return null;
		}
		return (T) property.getValue();
	}
}
