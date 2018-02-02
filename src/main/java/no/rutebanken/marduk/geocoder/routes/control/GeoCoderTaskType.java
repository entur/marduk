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

package no.rutebanken.marduk.geocoder.routes.control;

import static no.rutebanken.marduk.geocoder.GeoCoderConstants.*;

public enum GeoCoderTaskType {
	ADDRESS_DOWNLOAD(KARTVERKET_ADDRESS_DOWNLOAD), ADMINISTRATIVE_UNITS_DOWNLOAD(KARTVERKET_ADMINISTRATIVE_UNITS_DOWNLOAD),
	PLACE_NAMES_DOWNLOAD(KARTVERKET_PLACE_NAMES_DOWNLOAD), TIAMAT_POI_UPDATE(TIAMAT_PLACES_OF_INTEREST_UPDATE_START),
	TIAMAT_ADMINISTRATIVE_UNITS_UPDATE(TIAMAT_ADMINISTRATIVE_UNITS_UPDATE_START),
	TIAMAT_NEIGHBOURING_COUNTRIES_UPDATE(TIAMAT_NEIGHBOURING_COUNTRIES_UPDATE_START), TIAMAT_EXPORT(TIAMAT_EXPORT_START),
	PELIAS_UPDATE(PELIAS_UPDATE_START);

	GeoCoderTaskType(GeoCoderTask geoCoderTask) {
		this.geoCoderTask = geoCoderTask;
	}

	private GeoCoderTask geoCoderTask;

	public GeoCoderTask getGeoCoderTask() {
		return geoCoderTask;
	}
}
