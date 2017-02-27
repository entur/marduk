package no.rutebanken.marduk.geocoder.netex;


import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Polygon;
import net.opengis.gml._3.AbstractRingPropertyType;
import net.opengis.gml._3.DirectPositionListType;
import net.opengis.gml._3.LinearRingType;
import net.opengis.gml._3.PolygonType;

import java.util.ArrayList;
import java.util.List;

public class NetexGeoUtil {


	public static PolygonType toNetexPolygon(Polygon polygon) {
		LinearRingType linearRing = new LinearRingType();

		List<Double> values = new ArrayList<>();
		for (Coordinate coordinate : polygon.getExteriorRing().getCoordinates()) {
			values.add(coordinate.y); // lat
			values.add(coordinate.x); // lon
		}

		// Ignoring interior rings because the corresponding exclaves are not handled.

		DirectPositionListType positionList = new DirectPositionListType().withValue(values);
		linearRing.withPosList(positionList);

		return new PolygonType()
				       .withExterior(new AbstractRingPropertyType().withAbstractRing(
						       new net.opengis.gml._3.ObjectFactory().createLinearRing(linearRing)));
	}
}
