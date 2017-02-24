package no.rutebanken.marduk.geocoder.routes.kartverket;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.SystemStatus;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.geocoder.GeoCoderConstants.*;

@Component
public class PlaceNamesDownloadRouteBuilder extends BaseRouteBuilder {
	/**
	 * One time per 24H on MON-FRI
	 */
	@Value("${kartverket.place.names.download.cron.schedule:0+0+23+?+*+MON-FRI}")
	private String cronSchedule;

	@Value("${kartverket.place.names.blobstore.subdirectory:kartverket}")
	private String blobStoreSubdirectoryForKartverket;


	@Value("${kartverket.place.names.dataSetId:stedsnavn-ssr-wgs84-geojson}")
	private String placeNamesDataSetId;

	private static final String FORMAT_GEO_JSON = "geoJSON";

	@Override
	public void configure() throws Exception {
		super.configure();

		singletonFrom("quartz2://marduk/placeNamesDownload?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
				.autoStartup("{{kartverket.place.names.download.autoStartup:false}}")
				.log(LoggingLevel.INFO, "Quartz triggers download of place names.")
				.setBody(constant(KARTVERKET_PLACE_NAMES_DOWNLOAD))
				.to("direct:geoCoderStart")
				.routeId("place-names-download-quartz");

		from(KARTVERKET_PLACE_NAMES_DOWNLOAD.getEndpoint())
				.log(LoggingLevel.INFO, "Start downloading place names")
				.process(e -> SystemStatus.builder(e).start(SystemStatus.Action.FILE_TRANSFER).entity("Kartverket place names").build()).to("direct:updateSystemStatus")
				.to("direct:transferPlaceNamesFiles")
				.choice()
				.when(simple("${header." + CONTENT_CHANGED + "}"))
				.log(LoggingLevel.INFO, "Uploaded updated place names from mapping authority. Initiating update of Tiamat")
				.setBody(constant(null))
				.setProperty(GEOCODER_NEXT_TASK, constant(PELIAS_UPDATE_START))
				.otherwise()
				.log(LoggingLevel.INFO, "Finished downloading place names from mapping authority with no changes")
				.end()
				.process(e -> SystemStatus.builder(e).state(SystemStatus.State.OK).build()).to("direct:updateSystemStatus")
				.routeId("place-names-download");


		from("direct:transferPlaceNamesFiles")
				.log(LoggingLevel.INFO, "Downloading place names per county")
				.setHeader(KARTVERKET_DATASETID, constant(placeNamesDataSetId))
				.setHeader(FOLDER_NAME, constant(blobStoreSubdirectoryForKartverket + "/placeNames"))
				.setHeader(KARTVERKET_FORMAT, constant(FORMAT_GEO_JSON))
				.to("direct:uploadUpdatedFiles")
				.routeId("place-names-to-blobstore");

	}


}
