package no.rutebanken.marduk.geocoder.routes.pelias.mapper;


import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import org.junit.Assert;
import org.junit.Test;

public class GeometryTransformationTest {


	@Test
	public void testConvertCoordinateFromUTM33N() throws Exception {
		GeometryFactory factory = new GeometryFactory();
		assertCoordinates(GeometryTransformer.fromUTM(factory.createPoint(new Coordinate(0, 0)), "33"), 10.51, 0);
		assertCoordinates(GeometryTransformer.fromUTM(factory.createPoint(new Coordinate(99999, 99999)), "33"), 11.41, 0.90);
	}


	@Test
	public void testConvertCoordinateFromUTM32N() throws Exception {
		GeometryFactory factory = new GeometryFactory();
		assertCoordinates(GeometryTransformer.fromUTM(factory.createPoint(new Coordinate(0, 0)), "32"), 4.51, 0);
		assertCoordinates(GeometryTransformer.fromUTM(factory.createPoint(new Coordinate(99999, 99999)), "32"), 5.41, 0.90);
	}

	private void assertCoordinates(Geometry geometry, double expectedX, double expectedY) {
		Coordinate coordinate = geometry.getCoordinate();
		Assert.assertEquals(expectedX, coordinate.x, 0.1);
		Assert.assertEquals(expectedY, coordinate.y, 0.1);
	}
}
