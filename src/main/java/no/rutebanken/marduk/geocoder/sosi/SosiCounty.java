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

public class SosiCounty extends SosiElementWrapper {

    public static final String OBJECT_TYPE = "Fylke";

    public SosiCounty(SosiElement sosiElement, SosiCoordinates coordinates) {
        super(sosiElement, coordinates);
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
