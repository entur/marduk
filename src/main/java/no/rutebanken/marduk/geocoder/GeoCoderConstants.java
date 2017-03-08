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

	public static final GeoCoderTask TIAMAT_PLACES_OF_INTEREST_UPDATE_START
			= new GeoCoderTask(GeoCoderTask.Phase.TIAMAT_UPDATE, "direct:tiamatPlacesOfInterestUpdate");

	public static final GeoCoderTask TIAMAT_EXPORT_START
			= new GeoCoderTask(GeoCoderTask.Phase.TIAMAT_EXPORT, "direct:tiamatExport");

	public static final GeoCoderTask TIAMAT_EXPORT_POLL
			= new GeoCoderTask(GeoCoderTask.Phase.TIAMAT_EXPORT, 1, "direct:tiamatPollJobStatus");

	public static final GeoCoderTask PELIAS_UPDATE_START =
			new GeoCoderTask(GeoCoderTask.Phase.PELIAS_UPDATE, "direct:peliasUpdate");

	public static final GeoCoderTask PELIAS_ES_SCRATCH_STATUS_POLL =
			new GeoCoderTask(GeoCoderTask.Phase.PELIAS_UPDATE, 1, "direct:pollElasticsearchScratchStatus");

	public static final GeoCoderTask PELIAS_ES_SCRATCH_STOP =
			new GeoCoderTask(GeoCoderTask.Phase.PELIAS_UPDATE, 2, "direct:shutdownElasticsearchScratchInstance");

}
