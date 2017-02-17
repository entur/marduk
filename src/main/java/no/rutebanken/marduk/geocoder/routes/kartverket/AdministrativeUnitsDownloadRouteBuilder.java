package no.rutebanken.marduk.geocoder.routes.kartverket;


import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.SystemStatus;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;

@Component
public class AdministrativeUnitsDownloadRouteBuilder extends BaseRouteBuilder {

	/**
	 * One time per 24H on MON-FRI
	 */
	@Value("${kartverket.administrative.units.download.cron.schedule:0+*+*/23+?+*+MON-FRI}")
	private String cronSchedule;

	@Value("${kartverket.blobstore.subdirectory:kartverket}")
	private String blobStoreSubdirectoryForKartverket;


	@Value("${kartverket.administrative.units.dataSetId:administrative-enheter-norge-wgs-84-hele-landet-geojson}")
	private String administrativeUnitsDataSetId;


	@Override
	public void configure() throws Exception {
		super.configure();

		singletonFrom("quartz2://marduk/administrativeUnitsDownload?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
				.autoStartup("{{kartverket.admin.units.download.autoStartup:false}}")
				.log(LoggingLevel.INFO, "Quartz triggers download of administrative units.")
				.to("activemq:queue:AdministrativeUnitsDownloadQueue")
				.routeId("admin-units-download-quartz");

		singletonFrom("activemq:queue:AdministrativeUnitsDownloadQueue?transacted=true&messageListenerContainerFactoryRef=batchListenerContainerFactory")
				.autoStartup("{{kartverket.admin.units.download.autoStartup:false}}")
				.transacted()
				.log(LoggingLevel.INFO, "Start downloading administrative units")
				.process(e -> SystemStatus.builder(e).start(SystemStatus.Action.FILE_TRANSFER).entity("Kartverket administrative units").build()).to("direct:updateSystemStatus")
				.to("direct:transferAdministrativeUnitsFile")
				.choice()
				.when(simple("${header." + CONTENT_CHANGED + "}"))
				.log(LoggingLevel.INFO, "Uploaded updated administrative units from mapping authority. Initiating update of Tiamat")
				.setBody(constant(null))
				.to("activemq:queue:TiamatAdministrativeUnitsUpdateQueue")
				.otherwise()
				.log(LoggingLevel.INFO, "Finished downloading administrative units from mapping authority with no changes")
				.end()
				.process(e -> SystemStatus.builder(e).state(SystemStatus.State.OK).build()).to("direct:updateSystemStatus")
				.routeId("admin-units-download");

		from("direct:transferAdministrativeUnitsFile")
				.setHeader(KARTVERKET_DATASETID, constant(administrativeUnitsDataSetId))
				.setHeader(FOLDER_NAME, constant(blobStoreSubdirectoryForKartverket))
				.to("direct:uploadUpdatedFiles")
				.routeId("administrative-units-to-blobstore");
	}

}
