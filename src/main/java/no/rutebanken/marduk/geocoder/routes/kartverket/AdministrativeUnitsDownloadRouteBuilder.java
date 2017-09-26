package no.rutebanken.marduk.geocoder.routes.kartverket;


import no.rutebanken.marduk.geocoder.routes.control.GeoCoderTaskType;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.geocoder.GeoCoderConstants.*;

@Component
public class AdministrativeUnitsDownloadRouteBuilder extends BaseRouteBuilder {

	/**
	 * One time per 24H on MON-FRI
	 */
	@Value("${kartverket.administrative.units.download.cron.schedule:0+0+23+?+*+MON-FRI}")
	private String cronSchedule;

	@Value("${kartverket.blobstore.subdirectory:kartverket}")
	private String blobStoreSubdirectoryForKartverket;

	@Value("${kartverket.administrative.units.dataSetId:administrative-enheter-norge-utm-33-hele-landet}")
	private String administrativeUnitsDataSetId;


	@Override
	public void configure() throws Exception {
		super.configure();

		singletonFrom("quartz2://marduk/administrativeUnitsDownload?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
				.autoStartup("{{kartverket.administrative.units.download.autoStartup:false}}")
				.filter(e -> isLeader(e.getFromRouteId()))
				.log(LoggingLevel.INFO, "Quartz triggers download of administrative units.")
				.setBody(constant(KARTVERKET_ADMINISTRATIVE_UNITS_DOWNLOAD))
				.to("direct:geoCoderStart")
				.routeId("admin-units-download-quartz");

		from(KARTVERKET_ADMINISTRATIVE_UNITS_DOWNLOAD.getEndpoint())
				.log(LoggingLevel.INFO, "Start downloading administrative units")
				.process(e -> JobEvent.systemJobBuilder(e).startGeocoder(GeoCoderTaskType.ADMINISTRATIVE_UNITS_DOWNLOAD).build()).to("direct:updateStatus")
				.to("direct:transferAdministrativeUnitsFile")
				.choice()
				.when(simple("${header." + CONTENT_CHANGED + "}"))
				.log(LoggingLevel.INFO, "Uploaded updated administrative units from mapping authority. Initiating update of Tiamat")
				.setBody(constant(null))
				.setProperty(GEOCODER_NEXT_TASK, constant(TIAMAT_ADMINISTRATIVE_UNITS_UPDATE_START))
				.otherwise()
				.log(LoggingLevel.INFO, "Finished downloading administrative units from mapping authority with no changes")
				.end()
				.process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build()).to("direct:updateStatus")
				.routeId("admin-units-download");

		from("direct:transferAdministrativeUnitsFile")
				.setHeader(KARTVERKET_DATASETID, constant(administrativeUnitsDataSetId))
				.setHeader(FOLDER_NAME, constant(blobStoreSubdirectoryForKartverket + "/administrativeUnits"))
				.to("direct:uploadUpdatedFiles")
				.routeId("administrative-units-to-blobstore");
	}

}
