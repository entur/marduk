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


import org.opengis.feature.simple.SimpleFeature;

public class KartverketCounty extends AbstractKartverketGeojsonAdapter {

	public static final String OBJECT_TYPE="Fylke";

	public KartverketCounty(SimpleFeature feature) {
		super(feature);
	}

	@Override
	public String getIsoCode() {
		return "NO-" + getId();
	}

	@Override
	public String getId() {
		return pad(getProperty("fylkesnr"), 2);
	}

	@Override
	public AbstractKartverketGeojsonAdapter.Type getType() {
		return Type.COUNTY;
	}

}
