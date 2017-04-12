package no.rutebanken.marduk.geocoder.routes.pelias.mapper.kartverket;

import no.rutebanken.marduk.geocoder.netex.TopographicPlaceAdapter;
import no.rutebanken.marduk.geocoder.netex.sosi.SosiTopographicPlaceReader;
import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Collection;
import java.util.stream.Collectors;

@Service
public class KartverketSosiStreamToElasticsearchCommands {
    public Collection<ElasticsearchCommand> transform(InputStream placeNamesStream) {
        return new SosiTopographicPlaceReader(placeNamesStream).read().stream()
                       .map(w -> ElasticsearchCommand.peliasIndexCommand(createMapper(w).toPeliasDocument())).collect(Collectors.toList());
    }

    TopographicPlaceAdapterToPeliasDocument createMapper(TopographicPlaceAdapter wrapper) {

        switch (wrapper.getType()) {

            case COUNTY:
                return new CountyToPeliasDocument(wrapper);
            case LOCALITY:
                return new LocalityToPeliasDocument(wrapper);
            case BOROUGH:
                return new BoroughToPeliasDocument(wrapper);
        }
        return null;
    }
}
