package no.rutebanken.marduk.geocoder.sosi;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceAdapter;
import no.rutebanken.marduk.geocoder.routes.pelias.mapper.coordinates.GeometryTransformer;
import no.rutebanken.marduk.geocoder.routes.pelias.mapper.kartverket.KartverketCoordinatSystemMapper;
import no.vegvesen.nvdb.sosi.Sosi;
import no.vegvesen.nvdb.sosi.document.*;
import no.vegvesen.nvdb.sosi.reader.SosiReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class SosiTopographicPlaceAdapterReader {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final String AREA_TYPE = "FLATE";

    private InputStream sosiInputStream;

    private File sosiFile;

    private double unit = 0.01;

    private String utmZone = "33";

    private Map<Long, List<Coordinate>> coordinatesMap = new HashMap<>();

    private Map<String, TopographicPlaceAdapter> adapterMap = new HashMap<>();

    public SosiTopographicPlaceAdapterReader(InputStream sosiInputStream) {
        this.sosiInputStream = sosiInputStream;
    }

    public SosiTopographicPlaceAdapterReader(File sosiFile) {
        this.sosiFile = sosiFile;
    }

    public Collection<TopographicPlaceAdapter> read() {
        try {
            readToAdapterMap();
        } catch (IOException ioE) {
            throw new RuntimeException("Failed to read topographic places from SOSI: " + ioE.getMessage(), ioE);
        }
        return adapterMap.values();
    }

    /**
     * Read content from SOSI file
     * <p>
     * 1. Map all shapes with coordinates
     * 2. Read all areas and wrap in TopographicPlaceAdapter
     *
     * @throws IOException
     */
    private void readToAdapterMap() throws IOException {
        if (sosiInputStream == null) {
            sosiInputStream = new FileInputStream(sosiFile);
        }
        SosiReader reader = Sosi.createReader(sosiInputStream);

        SosiDocument doc = reader.read();
        readHeaderValues(doc.getHead());
        doc.getElements().forEach(se -> collectCoordinates(se));
        doc.getElements().forEach(se -> collectAdminUnits(se));
        sosiInputStream.close();
    }

    private void collectAdminUnits(SosiElement sosiElement) {
        if (sosiElement.getName().equals(AREA_TYPE) && sosiElement.hasSubElements()) {

            TopographicPlaceAdapter area = SosiElementWrapperFactory.createWrapper(sosiElement, coordinatesMap);
            if (area != null) {
                String id = area.getId();
                TopographicPlaceAdapter existingArea = adapterMap.get(id);
                if (shouldAddNewArea(area, existingArea)) {
                    adapterMap.put(id, area);
                }
            }
        }
    }

    /**
     * To avoid duplicates exclaves are discarded.
     * <p>
     * Area is added if id does not already exist or if area is greater than existing area for id.
     */
    private boolean shouldAddNewArea(TopographicPlaceAdapter area, TopographicPlaceAdapter existingArea) {
        if (existingArea == null) {
            return true;
        }
        Geometry areaGeo = area.getDefaultGeometry();
        Geometry existingAreaGeo = existingArea.getDefaultGeometry();

        if (existingAreaGeo == null) {
            return areaGeo != null;
        }

        return areaGeo != null && areaGeo.getArea() > existingAreaGeo.getArea();
    }


    private void collectCoordinates(SosiElement sosiElement) {
        if (sosiElement.getName().equals("KURVE") || sosiElement.getName().equals("BUEP")) {

            Long id = sosiElement.getValueAs(SosiSerialNumber.class).longValue();

            List<SosiNumber> sosiNumbers = new ArrayList<>();
            sosiElement.subElements().filter(se -> "NØ".equals(se.getName())).forEach(se -> sosiNumbers.addAll(se.getValuesAs(SosiNumber.class)));

            List<Coordinate> coordinates = toLatLonCoordinates(sosiNumbers);

            coordinatesMap.put(id, coordinates);
        }

    }

    private void readHeaderValues(SosiElement head) {
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

    private List<Coordinate> toLatLonCoordinates(List<SosiNumber> sosiNumbers) {
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
