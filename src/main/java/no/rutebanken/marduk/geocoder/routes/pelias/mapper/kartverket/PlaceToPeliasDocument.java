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

package no.rutebanken.marduk.geocoder.routes.pelias.mapper.kartverket;


import no.rutebanken.marduk.geocoder.netex.TopographicPlaceAdapter;
import org.apache.commons.lang3.StringUtils;

public class PlaceToPeliasDocument extends TopographicPlaceAdapterToPeliasDocument {

    private long popularity;

    public PlaceToPeliasDocument(TopographicPlaceAdapter simpleFeature, long popularity) {
        super(simpleFeature);
        this.popularity = popularity;
    }

    @Override
    protected Long getPopularity() {
        return popularity;
    }

    @Override
    protected String getLayer() {
        return "address";
    }

    @Override
    protected String getLocalityId() {
        return feature.getParentId();
    }

    @Override
    protected String getCountyId() {
        return StringUtils.substring(getLocalityId(), 0, 2);
    }


}


