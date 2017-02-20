package no.rutebanken.marduk.geocoder.routes.kartverket.geojson;

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
		String targetFile = "files/fylker-filtered.geojson";
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
