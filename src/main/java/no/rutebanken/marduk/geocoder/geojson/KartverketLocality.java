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

package no.rutebanken.marduk.geocoder.geojson;


import net.logstash.logback.encoder.org.apache.commons.lang.StringUtils;
import org.opengis.feature.simple.SimpleFeature;

public class KartverketLocality extends AbstractKartverketGeojsonAdapter {

	public static final String OBJECT_TYPE = "Kommune";

	public KartverketLocality(SimpleFeature feature) {
		super(feature);
	}

	@Override
	public String getId() {
		return pad(getProperty("komm"), 4);
	}

	@Override
	public String getParentId() {
		return StringUtils.substring(getId(), 0, 2);
	}

	@Override
	public AbstractKartverketGeojsonAdapter.Type getType() {
		return Type.LOCALITY;
	}

}


