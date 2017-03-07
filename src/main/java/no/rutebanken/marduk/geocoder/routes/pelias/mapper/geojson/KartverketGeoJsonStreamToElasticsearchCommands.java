package no.rutebanken.marduk.geocoder.routes.pelias.mapper.geojson;

import no.rutebanken.marduk.geocoder.featurejson.FeatureJSONCollection;
import no.rutebanken.marduk.geocoder.geojson.*;
import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import org.opengis.feature.simple.SimpleFeature;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Collection;

@Service
public class KartverketGeoJsonStreamToElasticsearchCommands {
	public Collection<ElasticsearchCommand> transform(InputStream placeNamesStream) {
		return new FeatureJSONCollection(placeNamesStream)
				       .mapToList(f -> ElasticsearchCommand.peliasIndexCommand(createMapper(f).toPeliasDocument()));
	}

	KartverketFeatureToPeliasDocument createMapper(SimpleFeature feature) {

		AbstractKartverketGeojsonWrapper wrapper = KartverketFeatureWrapperFactory.createWrapper(feature);

		switch (wrapper.getType()) {

			case COUNTY:
				return new CountyToPeliasDocument((KartverketCounty) wrapper);
			case LOCALITY:
				return new LocalityToPeliasDocument((KartverketLocality) wrapper);
			case BOROUGH:
				return new BoroughToPeliasDocument((KartverketBorough) wrapper);
			case NEIGHBOURHOOD:
				return new NeighbourhoodToPeliasDocument((KartverketNeighbourhood) wrapper);
		}
		return null;
	}


}
