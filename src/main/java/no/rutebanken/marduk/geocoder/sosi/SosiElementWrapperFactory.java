package no.rutebanken.marduk.geocoder.sosi;

import com.vividsolutions.jts.geom.Coordinate;
import no.vegvesen.nvdb.sosi.document.SosiElement;
import no.vegvesen.nvdb.sosi.document.SosiString;

import java.util.List;
import java.util.Map;

public class SosiElementWrapperFactory {

    public static SosiElementWrapper createWrapper(SosiElement sosiElement, Map<Long, List<Coordinate>> geoRef) {
        SosiElement objectType = sosiElement.findSubElement(se -> "OBJTYPE".equals(se.getName())).get();

        if (objectType != null) {
            String type = objectType.getValueAs(SosiString.class).getString();

            if (SosiCounty.OBJECT_TYPE.equals(type)) {
                return new SosiCounty(sosiElement, geoRef);
            } else if (SosiLocality.OBJECT_TYPE.equals(type)) {
                return new SosiLocality(sosiElement, geoRef);
            }
        }

        return null;
    }
}
