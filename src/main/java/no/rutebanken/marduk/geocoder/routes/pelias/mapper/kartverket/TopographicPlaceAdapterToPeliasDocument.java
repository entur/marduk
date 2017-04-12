package no.rutebanken.marduk.geocoder.routes.pelias.mapper.kartverket;


import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;
import no.rutebanken.marduk.geocoder.geojson.AbstractKartverketGeojsonWrapper;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceAdapter;
import no.rutebanken.marduk.geocoder.routes.pelias.json.GeoPoint;
import no.rutebanken.marduk.geocoder.routes.pelias.json.Parent;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import org.geojson.LngLatAlt;
import org.geojson.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public abstract class TopographicPlaceAdapterToPeliasDocument {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public static final String SOURCE = "kartverket";

	protected TopographicPlaceAdapter feature;

	public TopographicPlaceAdapterToPeliasDocument(TopographicPlaceAdapter feature) {
		this.feature = feature;
	}


	public PeliasDocument toPeliasDocument() {
		PeliasDocument document = new PeliasDocument(getLayer(), SOURCE, feature.getId());

		document.setDefaultName(feature.getName());
		document.setCenterPoint(mapCenterPoint());
		document.setShape(mapShape());

		document.setParent(getParent());
		return document;
	}

	protected abstract String getLayer();

	protected String getLocalityId() {
		return null;
	}

	protected String getCountyId() {
		return null;
	}

	protected Parent getParent() {
		return Parent.builder()
				       .withCountryId("NOR")
				       .withLocalityId(getLocalityId())
				       .withCountyId(getCountyId())
				       .build();
	}

	protected GeoPoint mapCenterPoint() {
		Geometry geometry = feature.getDefaultGeometry();

		if (geometry != null && geometry.isValid()) {
			Point centroid = geometry.getCentroid();
			return new GeoPoint(centroid.getY(), centroid.getX());
		}

		return null;
	}

	protected Polygon mapShape() {
		Geometry geometry = feature.getDefaultGeometry();

		if (geometry instanceof com.vividsolutions.jts.geom.Polygon) {
			com.vividsolutions.jts.geom.Polygon jtsPolygon = (com.vividsolutions.jts.geom.Polygon) geometry;

			if (jtsPolygon.isValid()) {

				List<LngLatAlt> coord = Arrays.stream(jtsPolygon.getExteriorRing().getCoordinates()).map(c -> new LngLatAlt(c.x, c.y)).collect(Collectors.toList());
				return new Polygon(removeConsecutiveDuplicates(coord));
			} else {
				logger.warn("Ignoring polygon for kartverket feature with invalid geometry: " + getLayer() + ":" + feature.getName());
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

}

