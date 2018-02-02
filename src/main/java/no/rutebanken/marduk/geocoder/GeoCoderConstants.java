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

package no.rutebanken.marduk.geocoder;


import no.rutebanken.marduk.geocoder.routes.control.GeoCoderTask;

public class GeoCoderConstants {


	public static final String GEOCODER_NEXT_TASK = "RutebankenGeoCoderNextTask";
	public static final String GEOCODER_CURRENT_TASK = "RutebankenGeoCoderCurrentTask";
	public static final String GEOCODER_RESCHEDULE_TASK = "RutebankgenGeoCoderRescheduleTask";
	public static final String GEOCODER_ADMIN_UNIT_REPO="RutebankenGeoCoderAdminUnitRepository";

	public static final GeoCoderTask KARTVERKET_ADDRESS_DOWNLOAD
			= new GeoCoderTask(GeoCoderTask.Phase.DOWNLOAD_SOURCE_DATA, "direct:kartverketAddressDownload");

	public static final GeoCoderTask KARTVERKET_ADMINISTRATIVE_UNITS_DOWNLOAD
			= new GeoCoderTask(GeoCoderTask.Phase.DOWNLOAD_SOURCE_DATA, "direct:kartverketAdministrativeUnitsDownload");

	public static final GeoCoderTask KARTVERKET_PLACE_NAMES_DOWNLOAD
			= new GeoCoderTask(GeoCoderTask.Phase.DOWNLOAD_SOURCE_DATA, "direct:kartverketPlaceNamesDownload");

	public static final GeoCoderTask TIAMAT_ADMINISTRATIVE_UNITS_UPDATE_START
			= new GeoCoderTask(GeoCoderTask.Phase.TIAMAT_UPDATE, "direct:tiamatAdministrativeUnitsUpdate");

	public static final GeoCoderTask TIAMAT_NEIGHBOURING_COUNTRIES_UPDATE_START
			= new GeoCoderTask(GeoCoderTask.Phase.TIAMAT_UPDATE, "direct:tiamatNeighbouringCountriesUpdateStart");

	public static final GeoCoderTask TIAMAT_PLACES_OF_INTEREST_UPDATE_START
			= new GeoCoderTask(GeoCoderTask.Phase.TIAMAT_UPDATE, "direct:tiamatPlacesOfInterestUpdate");

	public static final GeoCoderTask TIAMAT_EXPORT_START
			= new GeoCoderTask(GeoCoderTask.Phase.TIAMAT_EXPORT, "direct:tiamatGeoCoderExport");

	public static final GeoCoderTask TIAMAT_EXPORT_POLL
			= new GeoCoderTask(GeoCoderTask.Phase.TIAMAT_EXPORT, 1, "direct:tiamatPollJobStatus");

	public static final GeoCoderTask PELIAS_UPDATE_START =
			new GeoCoderTask(GeoCoderTask.Phase.PELIAS_UPDATE, "direct:peliasUpdate");

	public static final GeoCoderTask PELIAS_ES_SCRATCH_STATUS_POLL =
			new GeoCoderTask(GeoCoderTask.Phase.PELIAS_UPDATE, 1, "direct:pollElasticsearchScratchStatus");

	public static final GeoCoderTask PELIAS_ES_SCRATCH_STOP =
			new GeoCoderTask(GeoCoderTask.Phase.PELIAS_UPDATE, 2, "direct:shutdownElasticsearchScratchInstance");

}
