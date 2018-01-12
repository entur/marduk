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
