package no.rutebanken.marduk.geocoder.routes.pelias.mapper.geojson;

import no.rutebanken.marduk.geocoder.featurejson.FeatureJSONCollection;
import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import org.opengis.feature.Property;
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

	AbstractKartverketFeatureToPeliasDocument createMapper(SimpleFeature feature) {
		Property typeProp = feature.getProperty("objtype");

		if (typeProp != null) {

			Object type = typeProp.getValue();
			if ("Fylke".equals(type)) {
				return new FylkeToPeliasDocument(feature);
			} else if ("Kommune".equals(type)) {
				return new KommuneToPeliasDocument(feature);
			} else if ("Grunnkrets".equals(type)) {
				return new GrunnkretsToPeliasDocument(feature);
			}
		}

		return new PlaceNameToPeliasDocument(feature);
	}


}
