package no.rutebanken.marduk.geocoder.routes.tiamat;


import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.geocoder.featurejson.FeatureJSONFilter;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceConverter;
import no.rutebanken.marduk.geocoder.netex.kartverket.GeoJsonTopographicPlaceReader;
import no.rutebanken.marduk.geocoder.routes.control.GeoCoderTaskType;
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

import static no.rutebanken.marduk.routes.status.SystemStatus.Entity.*;
import static no.rutebanken.marduk.routes.status.SystemStatus.System.*;
import static no.rutebanken.marduk.routes.status.SystemStatus.Action.*;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.TIMESTAMP;
import static no.rutebanken.marduk.geocoder.GeoCoderConstants.*;

@Component
public class TiamatAdministrativeUnitsUpdateRouteBuilder extends BaseRouteBuilder {

	@Value("${kartverket.blobstore.subdirectory:kartverket}")
	private String blobStoreSubdirectoryForKartverket;

	@Value("${tiamat.administrative.units.update.cron.schedule:0+0+23+?+*+MON-FRI}")
	private String cronSchedule;

	@Value("${tiamat.url}")
	private String tiamatUrl;

	@Value("${kartverket.download.directory:files/kartverket}")
	private String localWorkingDirectory;

	@Value("${kartverket.admin.units.archive.filename:Grensedata_Norge_WGS84_Adm_enheter_geoJSON.zip}")
	private String adminUnitsArchiveFileName;

	@Value("#{'${kartverket.admin.units.geojson.filenames:fylker.geojson,kommuner.geojson,grunnkretser.geojson}'.split(',')}")
	private List<String> adminUnitsFileNames;

	@Autowired
	private TopographicPlaceConverter topographicPlaceConverter;

	@Override
	public void configure() throws Exception {
		super.configure();

		singletonFrom("quartz2://marduk/tiamatTAdministrativeUnitsUpdate?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
				.autoStartup("{{tiamat.administrative.units.update.autoStartup:false}}")
				.log(LoggingLevel.INFO, "Quartz triggers Tiamat update of administrative units.")
				.setBody(constant(TIAMAT_ADMINISTRATIVE_UNITS_UPDATE_START))
				.to("direct:geoCoderStart")
				.routeId("tiamat-admin-units-update-quartz");

		from(TIAMAT_ADMINISTRATIVE_UNITS_UPDATE_START.getEndpoint())
				.setProperty(TIMESTAMP, simple("${date:now:yyyyMMddHHmmss}"))
				.log(LoggingLevel.INFO, "Starting update of administrative units in Tiamat")
				.process(e -> SystemStatus.builder(e).start(GeoCoderTaskType.TIAMAT_ADMINISTRATIVE_UNITS_UPDATE)
						              .action(SystemStatus.Action.UPDATE).source(GC).target(TIAMAT)
						              .entity(ADMINISTRATIVE_UNITS).build()).to("direct:updateSystemStatus")
				.to("direct:fetchAdministrativeUnits")
				.to("direct:filterAdministrativeUnitsExclaves")
				.to("direct:mapAdministrativeUnitsToNetex")
				.to("direct:updateAdministrativeUnitsInTiamat")
				.log(LoggingLevel.INFO, "Started job updating administrative units in Tiamat")
				.end()
				.routeId("tiamat-admin-units-update");

		from("direct:fetchAdministrativeUnits")
				.log(LoggingLevel.DEBUG, getClass().getName(), "Fetching latest administrative units ...")
				.setHeader(FILE_HANDLE, simple(blobStoreSubdirectoryForKartverket + "/administrativeUnits/" + adminUnitsArchiveFileName))
				.to("direct:getBlob")
				.process(e -> ZipFileUtils.unzipFile(e.getIn().getBody(InputStream.class), workingDirectory(e)))
				.routeId("tiamat-fetch-admin-units-geojson");

		from("direct:filterAdministrativeUnitsExclaves")
				.log(LoggingLevel.DEBUG, getClass().getName(), "Removing exclaves from administrative units ...")
				.process(e -> new FeatureJSONFilter(workingDirectory(e) + "/abas/fylker.geojson", workingDirectory(e) + "/fylker.geojson", "fylkesnr", "area").filter())
				.process(e -> new FeatureJSONFilter(workingDirectory(e) + "/abas/kommuner.geojson", workingDirectory(e) + "/kommuner.geojson", "komm", "area").filter())
				.process(e -> new FeatureJSONFilter(workingDirectory(e) + "/abas/grunnkretser.geojson", workingDirectory(e) + "/grunnkretser.geojson", "grunnkrets", "area").filter())
				.routeId("tiamat-filter-geojson-exclaves");


		from("direct:mapAdministrativeUnitsToNetex")
				.log(LoggingLevel.DEBUG, getClass().getName(), "Mapping latest administrative units to Netex ...")
				.process(e -> topographicPlaceConverter.toNetexFile(
						new GeoJsonTopographicPlaceReader(geoJsonFiles(e)),
						workingDirectory(e) + "/admin-units-netex.xml"))
				.routeId("tiamat-map-admin-units-geojson-to-netex");

		from("direct:updateAdministrativeUnitsInTiamat")
				//				.setHeader(Constants.JOB_ID, simple("${body.id}")) TODO import not ready
//				.setHeader(Constants.JOB_STATUS_URL, simple("${body.jobUrl}"))
				// clean up local folder now? Or after job is completed
				.setHeader(Constants.JOB_STATUS_ROUTING_DESTINATION, constant("direct:processTiamatAdministrativeUnitsUpdateCompleted"))
				.routeId("tiamat-admin-units-update-start");


		from("direct:processTiamatAdministrativeUnitsUpdateCompleted")
				.setProperty(GEOCODER_NEXT_TASK, constant(TIAMAT_EXPORT_START))
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
