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

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import no.vegvesen.nvdb.sosi.document.SosiElement;
import no.vegvesen.nvdb.sosi.document.SosiNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SosiPlace extends SosiElementWrapper {
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    public static final String OBJECT_TYPE = "Sted";

    public SosiPlace(SosiElement sosiElement, SosiCoordinates coordinates) {
        super(sosiElement, coordinates);
    }

    @Override
    public String getId() {
        return getProperty("IDENT", "LOKALID");
    }

    @Override
    public Type getType() {
        return Type.PLACE;
    }


    @Override
    protected String getNamePropertyName() {
        // Not applicable
        return null;
    }

    @Override
    protected Map<String, String> getNames() {
        if (names == null) {
            names = new HashMap<>();
            String name = getProperty("STEDSNAVN", "SKRIVEMÅTE", "LANGNAVN");
            String lang = getProperty("STEDSNAVN", "SPRÅK");
            if (lang == null) {
                lang = "nor";
            }
            names.put(lang, name);
        }
        return names;
    }

    @Override
    public boolean isValid() {
        if (SosiSpellingStatusCode.isActive(getProperty("SKRIVEMÅTE", "SKRIVEMÅTESTATUS"))) {
            return false;
        }

        return super.isValid();
    }

    @Override
    public Geometry getDefaultGeometry() {
        if (geometry != null) {
            return geometry;
        }
        List<SosiNumber> sosiNumbers = new ArrayList<>();
        sosiElement.subElements().filter(se -> "NØ".equals(se.getName())).forEach(se -> sosiNumbers.addAll(se.getValuesAs(SosiNumber.class)));

        List<Coordinate> coordinateList = coordinates.toLatLonCoordinates(sosiNumbers);

        if (coordinateList.isEmpty()) {
            return null;
        }

        geometry = new GeometryFactory().createPoint(coordinateList.get(0));
        return geometry;
    }

    @Override
    public List<String> getCategories() {
        String type = getProperty("NAVNEOBJEKTTYPE");
        List<String> categories = new ArrayList<>();
        if (type != null) {
            categories.add(type);
        }
        return categories;
    }
}
