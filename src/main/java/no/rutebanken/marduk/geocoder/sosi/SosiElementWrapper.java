package no.rutebanken.marduk.geocoder.sosi;

import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceAdapter;
import no.vegvesen.nvdb.sosi.document.SosiElement;
import no.vegvesen.nvdb.sosi.document.SosiRefIsland;
import no.vegvesen.nvdb.sosi.document.SosiRefNumber;
import no.vegvesen.nvdb.sosi.document.SosiValue;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class SosiElementWrapper implements TopographicPlaceAdapter {
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    protected SosiElement sosiElement;

    protected Map<Long, List<Coordinate>> geoRef;

    protected Geometry geometry;

    public SosiElementWrapper(SosiElement sosiElement, Map<Long, List<Coordinate>> geoRef) {
        this.sosiElement = sosiElement;
        this.geoRef = geoRef;
    }

    @Override
    public Geometry getDefaultGeometry() {
        if (geometry != null) {
            return geometry;
        }
        List<Coordinate> coordinates = new ArrayList<>();

        for (SosiValue ref : sosiElement.findSubElement(se -> "REF".equals(se.getName())).get().getValuesAs(SosiValue.class)) {

            if (ref instanceof SosiRefNumber) {
                SosiRefNumber sosiRefNumber = (SosiRefNumber) ref;
                Long refId = sosiRefNumber.longValue();
                List<Coordinate> coordinatesForRef = geoRef.get(refId);
                if (coordinatesForRef != null) {

                    if (!coordinatesForRef.isEmpty()) {
                        if (sosiRefNumber.isReversedOrder()) {
                            coordinates.addAll(Lists.reverse(coordinatesForRef));
                        } else {
                            coordinates.addAll(coordinatesForRef);
                        }

                    } else {
                        logger.info("Bad coord sequence for  SosiRef: " + refId + " for: " + getType() + ": " + getId() + ": " + getName());
                    }
                } else {
                    logger.info("Ignore unknown SosiRef: " + refId + " for: " + getType() + ": " + getId() + ": " + getName());
                }
            } else if (ref instanceof SosiRefIsland) {
                logger.info("Ignore SosiRefIsland (enclave) for: " + getType() + ": " + getId() + ": " + getName());
            }

        }

        if (coordinates.isEmpty()) {
            return null;
        }

        geometry = new GeometryFactory().createPolygon(coordinates.toArray(new Coordinate[coordinates.size()]));
        return geometry;
    }

    @Override
    public String getIsoCode() {
        return null;
    }

    @Override
    public String getParentId() {
        return null;
    }

    protected String getProperty(String... path) {

        SosiElement subElement = sosiElement;
        for (String propName : path) {
            subElement = subElement.findSubElement(se -> propName.equals(se.getName())).get();
            if (subElement == null) {
                break;
            }
        }

        if (subElement != null) {
            return subElement.getValueAs(SosiValue.class).getString();
        }
        return null;
    }

    protected String pad(String val, int length) {
        return StringUtils.leftPad(val, length, "0");
    }
}
