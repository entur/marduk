package no.rutebanken.marduk.geocoder.featurejson;

import org.geotools.feature.FeatureIterator;
import org.geotools.geojson.feature.FeatureJSON;
import org.opengis.feature.simple.SimpleFeature;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;


public class FeatureJSONCollection {

	private InputStream inputStream;

	public FeatureJSONCollection(InputStream inputStream) {
		this.inputStream = inputStream;
	}

	public void forEach(Consumer<SimpleFeature> action) {

		FeatureIterator<SimpleFeature> itr;
		try {
			itr = new FeatureJSON().streamFeatureCollection(inputStream);
		} catch (IOException ioE) {
			throw new RuntimeException("Failed to stream FeatureJSON: " + ioE.getMessage(), ioE);
		}

		while (itr.hasNext()) {
			SimpleFeature simpleFeature = itr.next();
			action.accept(simpleFeature);
		}
	}

	public <R> List<R> mapToList(Function<SimpleFeature, ? extends R> mapper) {
		List<R> list = new ArrayList<>();
		forEach(f -> list.add(mapper.apply(f)));
		return list;
	}

}
