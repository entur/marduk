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


import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.geojson.feature.FeatureJSON;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Create a copy of feature json file with only one instance per id. Only the instance with the greatest value for a comparable property is retained.
 */
public class FeatureJSONFilter {

	private String sourceFilePath;

	private String correlationProperty;

	private String targetFilePath;
	private String comparatorProperty;


	private final Map<Object, SimpleFeature> map = new HashMap<>();

	public FeatureJSONFilter(String sourceFilePath, String targetFilePath, String correlationProperty, String comparatorProperty) {
		this.sourceFilePath = sourceFilePath;
		this.targetFilePath = targetFilePath;
		this.correlationProperty = correlationProperty;
		this.comparatorProperty = comparatorProperty;
	}


	public void filter() {
		try {

			FeatureJSON fJson = new FeatureJSON();
			FeatureIterator<SimpleFeature> itr = fJson.streamFeatureCollection(FileUtils.openInputStream(new File(sourceFilePath)));
			while (itr.hasNext()) {
				SimpleFeature simpleFeature = itr.next();
				Object id = getProperty(simpleFeature, correlationProperty);
				SimpleFeature existing = map.get(id);

				if (existing == null || shouldNewReplaceExisting(simpleFeature, existing)) {
					map.put(id, simpleFeature);
				}
			}
			itr.close();

			DefaultFeatureCollection filteredCollection = new DefaultFeatureCollection();
			filteredCollection.addAll(map.values());

			fJson.writeFeatureCollection(filteredCollection, new FileOutputStream(targetFilePath));

		} catch (IOException ioE) {
			throw new RuntimeException("Filtering failed for featureJSON file: " + sourceFilePath, ioE);
		}
	}


	protected boolean shouldNewReplaceExisting(SimpleFeature newF, SimpleFeature existingF) {
		Comparable existingCmpVal = getProperty(existingF, comparatorProperty);
		Comparable newCmpVal = getProperty(newF, comparatorProperty);

		return ObjectUtils.compare(newCmpVal, existingCmpVal) > 0;
	}


	protected <T> T getProperty(Feature feature, String propertyName) {
		Property property = feature.getProperty(propertyName);
		if (property == null) {
			return null;
		}
		return (T) property.getValue();
	}
}