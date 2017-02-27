package no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex;


import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import net.opengis.gml._3.AbstractRingType;
import net.opengis.gml._3.LinearRingType;
import no.rutebanken.marduk.geocoder.routes.pelias.json.GeoPoint;
import no.rutebanken.marduk.geocoder.routes.pelias.json.Name;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import org.geojson.LngLatAlt;
import org.geojson.Polygon;
import org.rutebanken.netex.model.LocationStructure;
import org.rutebanken.netex.model.Place_VersionStructure;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractNetexPlaceToPeliasDocumentMapper<T extends Place_VersionStructure> {

	private String participantRef;

	private GeometryFactory geometryFactory = new GeometryFactory();

	public AbstractNetexPlaceToPeliasDocumentMapper(String participantRef) {
		this.participantRef = participantRef;
	}

	public PeliasDocument toPeliasDocument(T place) {
		PeliasDocument document = new PeliasDocument(getLayer(place), participantRef, place.getId());

		document.setName(new Name(place.getName().getValue(), null));

		if (place.getCentroid() != null) {
			LocationStructure loc = place.getCentroid().getLocation();
			document.setCenterPoint(new GeoPoint(loc.getLatitude().doubleValue(), loc.getLongitude().doubleValue()));
		}
		// TODO else? center_point required. Calculate form polygon (if present?) or ignore?

		if (place.getPolygon() != null) {
			// TODO issues with shape validation in elasticsearch. duplicate coords + intersections cause document to be discarded. is shape even used by pelias?
			document.setShape(toPolygon(place.getPolygon().getExterior().getAbstractRing().getValue()));
		}


		populateDocument(place, document);

		return document;
	}

	private Polygon toPolygon(AbstractRingType ring) {

		if (ring instanceof LinearRingType) {
			LinearRingType linearRing = (LinearRingType) ring;

			List<LngLatAlt> coordinates = new ArrayList<>();
			LngLatAlt coordinate = null;
			LngLatAlt prevCoordinate = null;
			for (Double val : linearRing.getPosList().getValue()) {
				if (coordinate == null) {
					coordinate = new LngLatAlt();
					coordinate.setLatitude(val);
				} else {
					coordinate.setLongitude(val);
					if (prevCoordinate == null || !equals(coordinate, prevCoordinate)) {
						coordinates.add(coordinate);
					}
					prevCoordinate = coordinate;
					coordinate = null;
				}
			}
			return new Polygon(coordinates);

		}
		return null;
	}

	private boolean equals(LngLatAlt coordinate, LngLatAlt other) {
		return other.getLatitude() == coordinate.getLatitude() && other.getLongitude() == coordinate.getLongitude();
	}

	protected abstract void populateDocument(T place, PeliasDocument document);

	protected abstract String getLayer(T place);

}
