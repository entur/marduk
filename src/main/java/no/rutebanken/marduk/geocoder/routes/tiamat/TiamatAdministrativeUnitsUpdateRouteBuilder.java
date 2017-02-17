package no.rutebanken.marduk.geocoder.routes.tiamat;


import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceConverter;
import no.rutebanken.marduk.geocoder.netex.kartverket.GeoJsonTopographicPlaceReader;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import no.rutebanken.marduk.routes.status.SystemStatus;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.util.List;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.TIMESTAMP;

@Component
public class TiamatAdministrativeUnitsUpdateRouteBuilder extends BaseRouteBuilder {

	@Value("${kartverket.place.names.blobstore.subdirectory:kartverket}")
	private String blobStoreSubdirectoryForKartverket;

	@Value("${tiamat.administrative.units.update.cron.schedule:0+*+*/23+?+*+MON-FRI}")
	private String cronSchedule;

	@Value("${tiamat.url}")
	private String tiamatUrl;

	@Value("${kartverket.download.directory:files/karverket}")
	private String localWorkingDirectory;

	@Value("${kartverket.admin.units.archive.filename:Grensedata_Norge_WGS84_Adm_enheter_geoJSON.zip}")
	private String adminUnitsArchiveFileName;

	@Value("#{'${kartverket.admin.units.geojson.filenames:abas/fylker.geojson,abas/kommuner.geojson,abas/grunnkretser.geojson}'.split(',')}")
	private List<String> adminUnitsFileNames;

	@Autowired
	private TopographicPlaceConverter topographicPlaceConverter;

	@Override
	public void configure() throws Exception {
		super.configure();

		singletonFrom("quartz2://marduk/tiamatTAdministrativeUnitsUpdate?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
				.autoStartup("{{tiamat.administrative.units.update.autoStartup:false}}")
				.log(LoggingLevel.INFO, "Quartz triggers Tiamat update of administrative units.")
				.to("activemq:queue:TiamatAdministrativeUnitsUpdateQueue")
				.routeId("tiamat-admin-units-update-quartz");

		singletonFrom("activemq:queue:TiamatAdministrativeUnitsUpdateQueue?transacted=true&messageListenerContainerFactoryRef=batchListenerContainerFactory")
				.autoStartup("{{tiamat.administrative.units.update.autoStartup:false}}")
				.transacted()
				.setProperty(TIMESTAMP, simple("${date:now:yyyyMMddHHmmss}"))
				.log(LoggingLevel.INFO, "Starting update of administrative units in Tiamat")
				.process(e -> SystemStatus.builder(e).start(SystemStatus.Action.UPDATE).entity("Tiamat Administrative units" + "").build()).to("direct:updateSystemStatus")
				.to("direct:fetchAdministrativeUnits")
				.to("direct:mapAdministrativeUnitsToNetex")
				.to("direct:updateAdministrativeUnitsInTiamat")
				.log(LoggingLevel.INFO, "Started job updating administrative units in Tiamat")
				.end()
				.routeId("tiamat-admin-units-update");

		from("direct:fetchAdministrativeUnits")
				.log(LoggingLevel.DEBUG, getClass().getName(), "Fetching latest administrative units ...")
				.setHeader(FILE_HANDLE, simple(blobStoreSubdirectoryForKartverket + "/" + adminUnitsArchiveFileName))
				.to("direct:getBlob")
				.process(e -> ZipFileUtils.unzipFile(e.getIn().getBody(InputStream.class), workingDirectory(e)))
				.routeId("tiamat-fetch-admin-units-geojson");

		from("direct:mapAdministrativeUnitsToNetex")
				.log(LoggingLevel.DEBUG, getClass().getName(), "Mapping latest administrative units to Netex ...")
				.process(e -> topographicPlaceConverter.toNetexFile(
						new GeoJsonTopographicPlaceReader(geoJsonFiles(e)),
						workingDirectory(e) + "/admin-units-netex.xml")) //TODO
				.routeId("tiamat-map-admin-units-geojson-to-netex");

		from("direct:updateAdministrativeUnitsInTiamat")
				//				.setHeader(Constants.JOB_ID, simple("${body.id}")) TODO import not ready
//				.setHeader(Constants.JOB_STATUS_URL, simple("${body.jobUrl}"))
				.setHeader(Constants.JOB_STATUS_ROUTING_DESTINATION, constant("direct:processTiamatAdministrativeUnitsUpdateCompleted"))
				.routeId("tiamat-admin-units-update-start");


		from("direct:processTiamatAdministrativeUnitsUpdateCompleted")
				.to("activemq:queue:PeliasUpdateQueue")
				.process(e -> SystemStatus.builder(e).state(SystemStatus.State.OK).build()).to("direct:updateSystemStatus")
				.routeId("tiamat-admin-units-update-completed");
	}


	private File[] geoJsonFiles(Exchange e) {
		return adminUnitsFileNames.stream().map(f -> new File(workingDirectory(e) + "/" + f)).toArray(File[]::new);
	}

	private String workingDirectory(Exchange e) {
		return localWorkingDirectory + "/" + e.getProperty(TIMESTAMP);
	}
}
