package no.rutebanken.marduk.geocoder.routes.pelias.mapper.geojson;

import no.rutebanken.marduk.geocoder.featurejson.FeatureJSONCollection;
import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Collection;

@Service
public class PlaceNamesStreamToElasticsearchCommands {

	public Collection<ElasticsearchCommand> transform(InputStream placeNamesStream) {
		return new FeatureJSONCollection(placeNamesStream)
				       .mapToList(f -> ElasticsearchCommand.peliasIndexCommand(new PlaceNameToPeliasDocument(f).toPeliasDocument()));
	}
}
