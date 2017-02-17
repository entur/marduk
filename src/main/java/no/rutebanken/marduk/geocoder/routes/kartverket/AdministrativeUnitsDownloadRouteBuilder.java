package no.rutebanken.marduk.geocoder.routes.kartverket;


import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;

@Component
public class AdministrativeUnitsDownloadRouteBuilder extends BaseRouteBuilder {

	/**
	 * One time per 24H on MON-FRI
	 */
	@Value("${kartverket.topographic.place.download.cron.schedule:0+*+*/23+?+*+MON-FRI}")
	private String cronSchedule;

	@Value("${kartverket.topographic.place.blobstore.subdirectory:kartverket/topographicPlace}")
	private String blobStoreSubdirectoryForTopographicPlaces;

	@Value("${kartverket.administrative.units.blobstore.subdirectory:kartverket/administrativeUnits}")
	private String blobStoreSubdirectoryForAdministrativeUnits;


	@Value("${kartverket.administrative.units.dataSetId:administrative-enheter-norge-wgs-84-hele-landet-geojson}")
	private String administrativeUnitsDataSetId;

	@Value("${kartverket.topographic.place.dataSetId:30caed2f-454e-44be-b5cc-26bb5c0110ca}")
	private String topographicPlaceDataSetId;

	private static final String FORMAT_GEO_JSON = "geoJSON";

	@Override
	public void configure() throws Exception {
		super.configure();

		singletonFrom("quartz2://marduk/topographicPlaceDownload?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
				.autoStartup("{{kartverket.topographic.place.download.autoStartup:false}}")
				.log(LoggingLevel.INFO, "Quartz triggers download of topographic place information.")
				.to("activemq:queue:TopographicPlaceDownloadQueue")
				.routeId("topographic-place-download-quartz");

		singletonFrom("activemq:queue:AdministrativeUnitsDownloadQueue?transacted=true&messageListenerContainerFactoryRef=batchListenerContainerFactory")
				.autoStartup("{{kartverket.topographic.place.download.autoStartup:false}}")
				.transacted()
				.log(LoggingLevel.INFO, "Start downloading topographic place information")
				.log(LoggingLevel.INFO, "Test ${header.RutebankenContentChanged}")
				.to("direct:transferAdministrativeUnitsFile")
				.to("direct:transferTopographicPlacesFilesPerFylke")
				.choice()
				.when(simple("${header." + CONTENT_CHANGED + "}"))
				.log(LoggingLevel.INFO, "Uploaded updated topographic place information from mapping authority. Initiating update of Tiamat")
				.setBody(constant(null))
				.to("activemq:queue:TiamatAdministrativeUnitsUpdateQueue")
				.otherwise()
				.log(LoggingLevel.INFO, "Finished downloading topographic place information from mapping authority with no changes")
				.endChoice()
				.routeId("topographic-place-download");


		// TODO separate route?
		from("direct:transferTopographicPlacesFilesPerFylke")
				.log(LoggingLevel.INFO, "Downloading topographic places per area")
				.setHeader(KARTVERKET_DATASETID, constant(topographicPlaceDataSetId))
				.setHeader(FOLDER_NAME, constant(blobStoreSubdirectoryForTopographicPlaces))
				.setHeader(KARTVERKET_FORMAT, constant(FORMAT_GEO_JSON))
				.to("direct:uploadUpdatedFiles")
				.routeId("topographic-place-per-fylke-to-blobstore");


		from("direct:transferAdministrativeUnitsFile")
				.log(LoggingLevel.INFO, "Downloading administrative units")
				.setHeader(KARTVERKET_DATASETID, constant(administrativeUnitsDataSetId))
				.setHeader(FOLDER_NAME, constant(blobStoreSubdirectoryForAdministrativeUnits))
				.to("direct:uploadUpdatedFiles")
				.routeId("administrative-units-to-blobstore");

	}

}
