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

package no.rutebanken.marduk.geocoder.routes.kartverket.geojson;

import no.rutebanken.marduk.geocoder.featurejson.FeatureJSONFilter;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.geojson.feature.FeatureJSON;
import org.junit.Assert;
import org.junit.Test;
import org.opengis.feature.Feature;

import java.io.FileInputStream;

public class FeatureJSONFilterTest {


	@Test
	public void testFilterFylkeByArea() throws Exception {
		String targetFile = "target/fylker-filtered.geojson";
		FeatureJSONFilter featureJSONFilter = new FeatureJSONFilter("src/test/resources/no/rutebanken/marduk/geocoder/geojson/fylker.geojson", targetFile, "fylkesnr", "area");

		featureJSONFilter.filter();

		FeatureJSON featureJSON = new FeatureJSON();
		FeatureCollection featureCollection = featureJSON.readFeatureCollection(new FileInputStream(targetFile));

		// 4 contains 4 fylke, buskerud twice. Expect only the largest buskerud to remain (20 > 5)
		Assert.assertEquals(3, featureCollection.size());
		Feature buskerud = getFeature(featureCollection, "Buskerud");
		Assert.assertNotNull(buskerud);
		Assert.assertEquals(Long.valueOf(20), buskerud.getProperty("area").getValue());

	}

	private Feature getFeature(FeatureCollection featureCollection, String name) {
		Feature feature = null;
		FeatureIterator itr = featureCollection.features();
		while (itr.hasNext()) {

			Feature candidate = itr.next();

			if (name.equals(candidate.getProperty("navn").getValue())) {
				feature = candidate;
				break;
			}
		}

		return feature;

	}
}
