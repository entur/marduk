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
import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.rutebanken.marduk.geocoder.sosi.SosiElementWrapperFactory;
import no.rutebanken.marduk.geocoder.sosi.SosiTopographicPlaceAdapterReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Collection;
import java.util.stream.Collectors;

@Service
public class KartverketSosiStreamToElasticsearchCommands {


    private SosiElementWrapperFactory sosiElementWrapperFactory;

    private final long placeBoost;

    public KartverketSosiStreamToElasticsearchCommands(@Autowired SosiElementWrapperFactory sosiElementWrapperFactory, @Value("${pelias.place.boost:3}") long placeBoost) {
        this.placeBoost = placeBoost;
        this.sosiElementWrapperFactory = sosiElementWrapperFactory;
    }


    public Collection<ElasticsearchCommand> transform(InputStream placeNamesStream) {
        return new SosiTopographicPlaceAdapterReader(sosiElementWrapperFactory, placeNamesStream).read().stream()
                       .map(w -> ElasticsearchCommand.peliasIndexCommand(createMapper(w).toPeliasDocument())).filter(d -> d != null).collect(Collectors.toList());
    }

    TopographicPlaceAdapterToPeliasDocument createMapper(TopographicPlaceAdapter wrapper) {

        switch (wrapper.getType()) {

            case COUNTY:
                return new CountyToPeliasDocument(wrapper);
            case LOCALITY:
                return new LocalityToPeliasDocument(wrapper);
            case BOROUGH:
                return new BoroughToPeliasDocument(wrapper);
            case PLACE:
                return new PlaceToPeliasDocument(wrapper, placeBoost);
        }
        return null;
    }
}
