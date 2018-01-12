package no.rutebanken.marduk.geocoder.sosi;

import com.vividsolutions.jts.geom.Coordinate;
import no.rutebanken.marduk.geocoder.routes.pelias.mapper.coordinates.GeometryTransformer;
import no.rutebanken.marduk.geocoder.routes.pelias.mapper.kartverket.KartverketCoordinatSystemMapper;
import no.vegvesen.nvdb.sosi.document.SosiElement;
import no.vegvesen.nvdb.sosi.document.SosiNumber;
import no.vegvesen.nvdb.sosi.document.SosiSerialNumber;
import no.vegvesen.nvdb.sosi.document.SosiValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SosiCoordinates {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private double unit = 0.01;

    private String utmZone = "33";


    private Map<Long, List<Coordinate>> coordinatesMap = new HashMap<>();

    public SosiCoordinates(SosiElement head) {
        SosiElement transpar = head.findSubElement(se -> "TRANSPAR".equals(se.getName())).get();

        if (transpar != null) {
            SosiElement unitElement = transpar.findSubElement(se -> "ENHET".equals(se.getName())).get();
            if (unitElement != null) {
                unit = unitElement.getValueAs(SosiNumber.class).doubleValue();
            } else {
                logger.warn("Unable to read unit from SOSI file header. Using default value: " + unit);
            }

            SosiElement coordSysElement = transpar.findSubElement(se -> "KOORDSYS".equals(se.getName())).get();
            if (coordSysElement != null) {
                utmZone = KartverketCoordinatSystemMapper.toUTMZone(coordSysElement.getValueAs(SosiValue.class).toString());
            } else {
                logger.warn("Unable to read utmZone from SOSI file header. Using default value: " + utmZone);
            }


        } else {
            logger.warn("Unable to read TRANSPAR from Sosi file header. Relying on default values.");
        }
    }

    public List<Coordinate> getForRef(Long ref) {
        return coordinatesMap.get(ref);
    }

    public void collectCoordinates(SosiElement sosiElement) {
        if (sosiElement.getName().equals("KURVE") || sosiElement.getName().equals("BUEP")) {

            Long id = sosiElement.getValueAs(SosiSerialNumber.class).longValue();

            List<SosiNumber> sosiNumbers = new ArrayList<>();
            sosiElement.subElements().filter(se -> "NØ".equals(se.getName())).forEach(se -> sosiNumbers.addAll(se.getValuesAs(SosiNumber.class)));

            List<Coordinate> coordinates = toLatLonCoordinates(sosiNumbers);

            coordinatesMap.put(id, coordinates);
        }

    }

    public List<Coordinate> toLatLonCoordinates(List<SosiNumber> sosiNumbers) {
        List<Coordinate> coordinates = new ArrayList<>();
        Double y = null;
        for (SosiNumber sosiNumber : sosiNumbers) {
            if (y == null) {
                y = sosiNumber.longValue() * unit;
            } else {
                Double x = sosiNumber.longValue() * unit;
                try {
                    Coordinate utmCoord = new Coordinate(x, y);
                    coordinates.add(GeometryTransformer.fromUTM(utmCoord, utmZone));
                } catch (Exception e) {
                    logger.warn("Failed to convert coordinates from utm to wgs84:" + e.getMessage(), e);
                }
                y = null;
            }
        }
        return coordinates;
    }

}
