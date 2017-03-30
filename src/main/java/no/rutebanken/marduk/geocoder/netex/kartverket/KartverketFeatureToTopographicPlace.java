package no.rutebanken.marduk.geocoder.netex.kartverket;


import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Polygon;
import net.opengis.gml._3.PolygonType;
import no.rutebanken.marduk.geocoder.geojson.AbstractKartverketGeojsonWrapper;
import no.rutebanken.marduk.geocoder.netex.NetexGeoUtil;
import org.rutebanken.netex.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KartverketFeatureToTopographicPlace {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private String participantRef;

    private AbstractKartverketGeojsonWrapper feature;

    public KartverketFeatureToTopographicPlace(AbstractKartverketGeojsonWrapper feature, String participantRef) {
        this.feature = feature;
        this.participantRef = participantRef;
    }


    public TopographicPlace toTopographicPlace() {
        return new TopographicPlace()
                       .withVersion("any").withModification(ModificationEnumeration.NEW)
                       .withName(multilingualString(feature.getName()))
                       .withDescriptor(new TopographicPlaceDescriptor_VersionedChildStructure().withName(multilingualString(feature.getName())))
                       .withTopographicPlaceType(getType())
                       .withPolygon(getPolygon())
                       .withIsoCode(feature.getIsoCode())
                       .withCountryRef(new CountryRef().withRef(getCountryRef()))
                       .withId(prefix(feature.getId()))
                       .withParentTopographicPlaceRef(toParentRef(feature.getParentId()));
    }


    protected String prefix(String id) {
        return participantRef + ":TopographicPlace:" + id;
    }

    protected TopographicPlaceRefStructure toParentRef(String id) {
        if (id == null) {
            return null;
        }
        return new TopographicPlaceRefStructure()
                       .withRef(prefix(feature.getParentId()));
    }

    protected TopographicPlaceTypeEnumeration getType() {
        switch (feature.getType()) {

            case COUNTY:
                return TopographicPlaceTypeEnumeration.COUNTY;
            case LOCALITY:
                return TopographicPlaceTypeEnumeration.TOWN;
            case BOROUGH:
                return TopographicPlaceTypeEnumeration.AREA;
        }
        return null;
    }


    private PolygonType getPolygon() {
        Geometry geometry = feature.getDefaultGeometry();

        if (geometry instanceof Polygon) {
            return NetexGeoUtil.toNetexPolygon((Polygon) geometry).withId(participantRef + "-" + feature.getId());
        }
        return null;
    }


    protected IanaCountryTldEnumeration getCountryRef() {
        return IanaCountryTldEnumeration.NO;
    }


    protected MultilingualString multilingualString(String val) {
        return new MultilingualString().withLang("en").withValue(val);
    }

}


