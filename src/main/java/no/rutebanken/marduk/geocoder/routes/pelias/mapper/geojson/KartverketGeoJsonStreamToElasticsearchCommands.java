package no.rutebanken.marduk.geocoder.routes.pelias.mapper.geojson;

import no.rutebanken.marduk.geocoder.featurejson.FeatureJSONCollection;
import no.rutebanken.marduk.geocoder.geojson.GeojsonFeatureWrapperFactory;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceAdapter;
import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.rutebanken.marduk.geocoder.routes.pelias.mapper.kartverket.*;
import org.opengis.feature.simple.SimpleFeature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Collection;
import java.util.stream.Collectors;

@Service
public class KartverketGeoJsonStreamToElasticsearchCommands {

    private final GeojsonFeatureWrapperFactory wrapperFactory;

    public KartverketGeoJsonStreamToElasticsearchCommands(@Autowired GeojsonFeatureWrapperFactory wrapperFactory) {
        this.wrapperFactory = wrapperFactory;
    }

    public Collection<ElasticsearchCommand> transform(InputStream placeNamesStream) {
        return new FeatureJSONCollection(placeNamesStream)
                       .mapToList(f -> createMapper(f).toPeliasDocument()).stream()
                       .filter(peliasDocument -> peliasDocument != null).map(pd -> ElasticsearchCommand.peliasIndexCommand(pd)).collect(Collectors.toList());
    }

    TopographicPlaceAdapterToPeliasDocument createMapper(SimpleFeature feature) {

        TopographicPlaceAdapter wrapper = wrapperFactory.createWrapper(feature);

        switch (wrapper.getType()) {

            case COUNTY:
                return new CountyToPeliasDocument(wrapper);
            case LOCALITY:
                return new LocalityToPeliasDocument(wrapper);
            case BOROUGH:
                return new BoroughToPeliasDocument(wrapper);
            case NEIGHBOURHOOD:
                return new NeighbourhoodToPeliasDocument(wrapper);
        }
        return null;
    }


}
