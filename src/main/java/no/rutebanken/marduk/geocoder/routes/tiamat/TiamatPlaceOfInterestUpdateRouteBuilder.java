package no.rutebanken.marduk.geocoder.routes.tiamat;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceConverter;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceReader;
import no.rutebanken.marduk.geocoder.netex.pbf.PbfTopographicPlaceReader;
import no.rutebanken.marduk.geocoder.routes.control.GeoCoderTaskType;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.SystemStatus;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.rutebanken.netex.model.IanaCountryTldEnumeration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.io.File;
import java.util.List;

import static no.rutebanken.marduk.routes.status.SystemStatus.Entity.*;
import static no.rutebanken.marduk.routes.status.SystemStatus.System.*;
import static no.rutebanken.marduk.routes.status.SystemStatus.Action.*;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.TIMESTAMP;
import static no.rutebanken.marduk.geocoder.GeoCoderConstants.*;

// TODO specific per source?
@Component
public class TiamatPlaceOfInterestUpdateRouteBuilder extends BaseRouteBuilder {
	/**
	 * One time per 24H on MON-FRI
	 */
	@Value("${tiamat.poi.update.cron.schedule:0+0+23+?+*+MON-FRI}")
	private String cronSchedule;

	@Value("${tiamat.poi.update.directory:files/tiamat/poi/update}")
	private String localWorkingDirectory;

	@Value("${tiamat.url}")
	private String tiamatUrl;

	@Value("${tiamat.publication.delivery.path:/jersey/publication_delivery}")
	private String tiamatPublicationDeliveryPath;

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
				.setBody(constant(TIAMAT_PLACES_OF_INTEREST_UPDATE_START))
				.to("direct:geoCoderStart")
				.routeId("tiamat-poi-update-quartz");

		from(TIAMAT_PLACES_OF_INTEREST_UPDATE_START.getEndpoint())
				.log(LoggingLevel.INFO, "Start updating POI information in Tiamat")
				.process(e -> SystemStatus.builder(e).start(GeoCoderTaskType.TIAMAT_POI_UPDATE).action(UPDATE).source(GC)
						              .target(TIAMAT).entity(POI).build()).to("direct:updateSystemStatus")
				.setProperty(TIMESTAMP, simple("${date:now:yyyyMMddHHmmss}"))
				.doTry()
				.to("direct:fetchPlaceOfInterest")
				.to("direct:mapPlaceOfInterestToNetex")
				.to("direct:updatePlaceOfInterestInTiamat")
				.to("direct:processTiamatPlaceOfInterestUpdateCompleted")
				.log(LoggingLevel.INFO, "Started job updating POI information in Tiamat")
				.doFinally()
				.to("direct:cleanUpLocalDirectory")
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
				.process(e -> topographicPlaceConverter.toNetexFile(createTopographicPlaceReader(e), workingDirectory(e) + "/poi-netex.xml"))
				.process(e-> e.getIn().setBody(new File(workingDirectory(e)+ "/poi-netex.xml")))
				.routeId("tiamat-map-poi-osm-to-netex");

		from("direct:updatePlaceOfInterestInTiamat")
				.setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
				.setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_XML))
//				.to(tiamatUrl + tiamatPublicationDeliveryPath)
				.routeId("tiamat-poi-update-start");

		from("direct:processTiamatPlaceOfInterestUpdateCompleted")
				.setProperty(GEOCODER_NEXT_TASK, constant(TIAMAT_EXPORT_START))
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
