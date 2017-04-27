package no.rutebanken.marduk.geocoder.sosi;

import com.vividsolutions.jts.geom.Coordinate;
import no.vegvesen.nvdb.sosi.document.SosiElement;

import java.util.List;
import java.util.Map;

public class SosiCounty extends SosiElementWrapper {

    public static final String OBJECT_TYPE = "Fylke";

    public SosiCounty(SosiElement sosiElement, Map<Long, List<Coordinate>> geoRef) {
        super(sosiElement, geoRef);
    }

    @Override
    public Type getType() {
        return Type.COUNTY;
    }

    @Override
    public String getId() {
        return pad(getProperty("FYLKESNUMMER"), 2);
    }

    @Override
    public String getIsoCode() {
        return "NO-" + getId();
    }

    @Override
    protected String getNamePropertyName() {
        return "FYLKESNAVN";
    }
}
