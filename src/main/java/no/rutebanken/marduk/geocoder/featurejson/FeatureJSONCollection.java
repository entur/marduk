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

package no.rutebanken.marduk.geocoder.featurejson;

import no.rutebanken.marduk.exceptions.FileValidationException;
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
			throw new FileValidationException("Failed to stream FeatureJSON: " + ioE.getMessage(), ioE);
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
