package no.rutebanken.marduk.geocoder.sosi;

import com.vividsolutions.jts.geom.Coordinate;
import no.vegvesen.nvdb.sosi.document.SosiElement;

import java.util.List;
import java.util.Map;

public class SosiLocality extends SosiElementWrapper {

    public static final String OBJECT_TYPE = "Kommune";

    public SosiLocality(SosiElement sosiElement, Map<Long, List<Coordinate>> geoRef) {
        super(sosiElement, geoRef);
    }

    @Override
    public Type getType() {
        return Type.LOCALITY;
    }

    @Override
    public String getId() {
        return pad(getProperty("KOMMUNENUMMER"),4);
    }

    @Override
    protected String getNamePropertyName() {
        return "KOMMUNENAVN";
    }

    @Override
    public String getParentId() {
        return getId().substring(0, 2);
    }
}
