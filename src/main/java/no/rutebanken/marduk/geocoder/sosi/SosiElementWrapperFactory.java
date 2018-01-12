package no.rutebanken.marduk.geocoder.sosi;

import no.vegvesen.nvdb.sosi.document.SosiElement;
import no.vegvesen.nvdb.sosi.document.SosiString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SosiElementWrapperFactory {

    public SosiElementWrapper createWrapper(SosiElement sosiElement, SosiCoordinates coordinates) {
        SosiElement objectType = sosiElement.findSubElement(se -> "OBJTYPE".equals(se.getName())).get();

        if (objectType != null) {
            String type = objectType.getValueAs(SosiString.class).getString();

            if (SosiCounty.OBJECT_TYPE.equals(type)) {
                return new SosiCounty(sosiElement, coordinates);
            } else if (SosiLocality.OBJECT_TYPE.equals(type)) {
                return new SosiLocality(sosiElement, coordinates);
            } else if (SosiPlace.OBJECT_TYPE.equals(type)) {
                return new SosiPlace(sosiElement, coordinates);
            }
        }

        return null;
    }
}
