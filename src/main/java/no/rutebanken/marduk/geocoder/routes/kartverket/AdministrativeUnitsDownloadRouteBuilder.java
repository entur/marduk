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

	@Value("${kartverket.administrative.units.county.dataSetId:6093c8a8-fa80-11e6-bc64-92361f002671}")
	private String countyDataSetId;


	@Value("${kartverket.administrative.units.municipality.dataSetId:041f1e6e-bdbc-4091-b48f-8a5990f3cc5b}")
	private String municipalityDataSetId;



	@Override
	public void configure() throws Exception {
		super.configure();

		singletonFrom("quartz2://marduk/administrativeUnitsDownload?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
				.autoStartup("{{kartverket.administrative.units.download.autoStartup:false}}")
				.filter(e -> isSingletonRouteActive(e.getFromRouteId()))
				.log(LoggingLevel.INFO, "Quartz triggers download of administrative units.")
				.setBody(constant(KARTVERKET_ADMINISTRATIVE_UNITS_DOWNLOAD))
				.inOnly("direct:geoCoderStart")
				.routeId("admin-units-download-quartz");

		from(KARTVERKET_ADMINISTRATIVE_UNITS_DOWNLOAD.getEndpoint())
				.log(LoggingLevel.INFO, "Start downloading administrative units")
				.process(e -> JobEvent.systemJobBuilder(e).startGeocoder(GeoCoderTaskType.ADMINISTRATIVE_UNITS_DOWNLOAD).build()).to("direct:updateStatus")
				.to("direct:transferCountyFile")
				.to("direct:transferMunicipalityFile")
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

		from("direct:transferCountyFile")
				.setHeader(KARTVERKET_DATASETID, constant(countyDataSetId))
				.setHeader(KARTVERKET_FORMAT, constant("SOSI"))
				.setHeader(FOLDER_NAME, constant(blobStoreSubdirectoryForKartverket + "/administrativeUnits/county"))
				.to("direct:uploadUpdatedFiles")
				.routeId("administrative-units-county-to-blobstore");

		from("direct:transferMunicipalityFile")
				.setHeader(KARTVERKET_DATASETID, constant(municipalityDataSetId))
				.setHeader(KARTVERKET_FORMAT, constant("SOSI"))
				.setHeader(FOLDER_NAME, constant(blobStoreSubdirectoryForKartverket + "/administrativeUnits/municipality"))
				.to("direct:uploadUpdatedFiles")
				.routeId("administrative-units-municipality-to-blobstore");
	}

}
