package no.rutebanken.marduk.geocoder.routes.tiamat;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceConverter;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceReader;
import no.rutebanken.marduk.geocoder.netex.pbf.PbfTopographicPlaceReader;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.SystemStatus;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.rutebanken.netex.model.IanaCountryTldEnumeration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.TIMESTAMP;

// TODO specific per source?
@Component
public class TiamatPlaceOfInterestUpdateRouteBuilder extends BaseRouteBuilder {
	/**
	 * One time per 24H on MON-FRI
	 */
	@Value("${tiamat.poi.update.cron.schedule:0+*+*/23+?+*+MON-FRI}")
	private String cronSchedule;

	@Value("${tiamat.poi.update.directory:files/tiamat/poi/update}")
	private String localWorkingDirectory;

	/**
	 * This is the name which the graph file is stored remotely.
	 */
	@Value("${otp.graph.file.name:norway-latest.osm.pbf}")
	private String osmFileName;

	@Value("${osm.pbf.blobstore.subdirectory:osm}")
	private String blobStoreSubdirectoryForOsm;

	@Value("#{'${osm.poi.filter:}'.split(',')}")
	private List<String> poiFilter;

	@Autowired
	private TopographicPlaceConverter topographicPlaceConverter;

	@Override
	public void configure() throws Exception {
		super.configure();

		singletonFrom("quartz2://marduk/tiamatPlaceOfInterestUpdate?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
				.autoStartup("{{tiamat.poi.update.autoStartup:false}}")
				.log(LoggingLevel.INFO, "Quartz triggers Tiamat update of place of interest.")
				.to("activemq:queue:TiamatPlaceOfInterestUpdateQueue")
				.routeId("tiamat-poi-update-quartz");

		singletonFrom("activemq:queue:TiamatPlaceOfInterestUpdateQueue?transacted=true&messageListenerContainerFactoryRef=batchListenerContainerFactory")
				.autoStartup("{{tiamat.poi.update.autoStartup:false}}")
				.transacted()
				.log(LoggingLevel.INFO, "Start updating POI information in Tiamat")
				.process(e -> SystemStatus.builder(e).start(SystemStatus.Action.UPDATE).entity("Tiamat POI").build()).to("direct:updateSystemStatus")
				.setProperty(TIMESTAMP, simple("${date:now:yyyyMMddHHmmss}"))
				.to("direct:fetchPlaceOfInterest")
				.to("direct:mapPlaceOfInterestToNetex")
				.to("direct:updatePlaceOfInterestInTiamat")
				.log(LoggingLevel.INFO, "Started job updating POI information in Tiamat")
				.end()
				.routeId("tiamat-poi-update");


		from("direct:fetchPlaceOfInterest")
				.log(LoggingLevel.DEBUG, getClass().getName(), "Fetching latest osm poi data ...")
				.setHeader(FILE_HANDLE, simple(blobStoreSubdirectoryForOsm + "/" + osmFileName))
				.to("direct:getBlob")
				.toD("file:" + localWorkingDirectory + "?fileName=${property." + TIMESTAMP + "}/" + osmFileName)
				.routeId("tiamat-fetch-poi-osm");

		from("direct:mapPlaceOfInterestToNetex")
				.log(LoggingLevel.DEBUG, getClass().getName(), "Mapping latest place of interest to Netex ...")
				.process(e -> topographicPlaceConverter.toNetexFile(createTopographicPlaceReader(e), workingDirectory(e) + "/poi-netex.xml")) //TODO
				.routeId("tiamat-map-poi-osm-to-netex");

		from("direct:updatePlaceOfInterestInTiamat")
				//.setHeader(Constants.JOB_ID, simple("${body.id}")) TODO import not ready
				// .setHeader(Constants.JOB_STATUS_URL, simple("${body.jobUrl}"))
				// clean up local dir now or after import completed
				.setHeader(Constants.JOB_STATUS_ROUTING_DESTINATION, constant("direct:processTiamatPlaceOfInterestUpdateCompleted"))
				.routeId("tiamat-poi-update-start");

		from("direct:processTiamatPlaceOfInterestUpdateCompleted")
				.to("activemq:queue:PeliasUpdateQueue")
				.process(e -> SystemStatus.builder(e).state(SystemStatus.State.OK).build()).to("direct:updateSystemStatus")
				.routeId("tiamat-poi-update-completed");

	}

	private TopographicPlaceReader createTopographicPlaceReader(Exchange e) {
		return new PbfTopographicPlaceReader(poiFilter, IanaCountryTldEnumeration.NO, new File(workingDirectory(e) + "/" + osmFileName));
	}

	private String workingDirectory(Exchange e) {
		return localWorkingDirectory + "/" + e.getProperty(TIMESTAMP);
	}
}
