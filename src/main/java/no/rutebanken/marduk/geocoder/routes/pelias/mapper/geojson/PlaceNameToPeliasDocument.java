package no.rutebanken.marduk.geocoder.routes.pelias.mapper.geojson;


import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;
import no.rutebanken.marduk.geocoder.routes.pelias.json.GeoPoint;
import no.rutebanken.marduk.geocoder.routes.pelias.json.Name;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;

import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;

public class PlaceNameToPeliasDocument {

	private SimpleFeature feature;

	public PlaceNameToPeliasDocument(SimpleFeature simpleFeature) {
		this.feature = simpleFeature;
	}

	public PeliasDocument toPeliasDocument() {
		PeliasDocument document = new PeliasDocument();

		document.setName(new Name(getProperty("enh_snavn"), null));
		document.setCenterPoint(mapCenterPoint());

		document.setLayer(PeliasDocument.LAYER_NEIGHBOURHOOD);
		document.setSource("Kartverket");
		document.setSourceId("SSR-ID:" + getProperty("enh_ssr_id"));

		return document;
	}

	private GeoPoint mapCenterPoint() {
		Object geometry = feature.getDefaultGeometryProperty().getValue();

		if (geometry instanceof Geometry) {
			Point centroid = ((Geometry) geometry).getCentroid();

			return new GeoPoint(centroid.getX(), centroid.getY());
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
