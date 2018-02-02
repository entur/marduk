/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

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
