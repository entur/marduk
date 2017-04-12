package no.rutebanken.marduk.geocoder.routes.pelias.mapper;


import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

public class GeometryTransformer {

    private static final String WGS84_EPSG = "EPSG:4326";

    private static GeometryTransformer instance;

    private CRSAuthorityFactory factory;

    private CoordinateReferenceSystem wgs84;


    private GeometryTransformer() throws FactoryException {
        factory = CRS.getAuthorityFactory(true);
        wgs84 = factory.createCoordinateReferenceSystem(WGS84_EPSG);

    }

    public static <T extends Geometry> T fromUTM(T geometry, String utmZone) throws FactoryException, TransformException {
        return getInstance().transformFromUTM(geometry, utmZone);
    }

    public static Coordinate fromUTM(Coordinate coordinate, String utmZone) throws FactoryException, TransformException {
        return getInstance().transformFromUTM(coordinate, utmZone);
    }


    public static Coordinate utmToUtm(Coordinate coordinate, String fromUtmZone, String toUtmZone) throws FactoryException, TransformException {
        return getInstance().transformUtmToUtm(coordinate, fromUtmZone, toUtmZone);
    }

    private Coordinate transformFromUTM(Coordinate coordinate, String utmZone) throws FactoryException, TransformException {
        return JTS.transform(coordinate, null, getMathTransform(utmZone));
    }


    private Coordinate transformUtmToUtm(Coordinate coordinate, String fromUtmZone, String toUtmZone) throws FactoryException, TransformException {
        return JTS.transform(coordinate, null, CRS.findMathTransform(utmCoordinateReferenceSystem(fromUtmZone), utmCoordinateReferenceSystem(toUtmZone)));
    }


    private <T extends Geometry> T transformFromUTM(T geometry, String utmZone) throws FactoryException, TransformException {
        return (T) JTS.transform(geometry, getMathTransform(utmZone));
    }

    private MathTransform getMathTransform(String fromUtmZone) throws FactoryException {
        return CRS.findMathTransform(utmCoordinateReferenceSystem(fromUtmZone), wgs84);
    }


    private CoordinateReferenceSystem utmCoordinateReferenceSystem(String utmZone) throws FactoryException {
        return factory.createCoordinateReferenceSystem("EPSG:326" + utmZone);
    }

    private static GeometryTransformer getInstance() throws FactoryException {
        if (instance == null) {
            instance = new GeometryTransformer();
        }
        return instance;
    }
}
