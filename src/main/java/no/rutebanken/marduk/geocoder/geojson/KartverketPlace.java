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

package no.rutebanken.marduk.geocoder.geojson;


import org.apache.commons.lang3.StringUtils;
import org.opengis.feature.simple.SimpleFeature;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class KartverketPlace extends AbstractKartverketGeojsonAdapter {

    private List<String> placeTypeWhiteList;

    public KartverketPlace(SimpleFeature feature, List<String> placeTypeWhiteList) {
        super(feature);
        this.placeTypeWhiteList = placeTypeWhiteList;
    }

    @Override
    public String getId() {
        return "" + getProperty("enh_ssr_id");
    }

    @Override
    public String getName() {
        return getProperty("enh_snavn");
    }

    @Override
    public String getParentId() {
        return StringUtils.leftPad("" + getProperty("enh_komm"), 4, "0");
    }

    @Override
    public AbstractKartverketGeojsonAdapter.Type getType() {
        return Type.PLACE;
    }

    @Override
    public boolean isValid() {
        if (!isValidType(getProperty("enh_navntype"))) {
            return false;
        }
        return KartverketFeatureSpellingStatusCode.isActive(getProperty("skr_snskrstat"));
    }

    private boolean isValidType(Long type) {
        if (placeTypeWhiteList == null) {
            return true;
        }
        return placeTypeWhiteList.contains(Objects.toString(type));
    }

    @Override
    public List<String> getCategories() {
        return Arrays.asList("" + getProperty("enh_navntype"));
    }
}
