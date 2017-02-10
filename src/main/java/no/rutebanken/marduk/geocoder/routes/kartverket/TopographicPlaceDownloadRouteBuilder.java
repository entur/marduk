package no.rutebanken.marduk.geocoder.routes.kartverket;


import no.rutebanken.marduk.geocoder.services.KartverketService;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TopographicPlaceDownloadRouteBuilder extends BaseRouteBuilder {

	/**
	 * One time per 24H on MON-FRI
	 */
	@Value("${kartverket.download.topographic.place.cron.schedule:0+*+*/23+?+*+MON-FRI}")
	private String cronSchedule;

	@Value("${kartverket.topographic.place.blobstore.subdirectory:kartverket/topographicPlace}")
	private String blobStoreSubdirectoryForTopographicPlaces;

	@Value("${kartverket.administrative.units.blobstore.subdirectory:kartverket/administrativeUnits}")
	private String blobStoreSubdirectoryForAdministrativeUnits;


	@Value("${kartverket.administrative.units.dataSetId:administrative-enheter-norge-wgs-84-hele-landet-geojson}")
	private String administrativeUnitsDataSetId;

	@Value("${kartverket.topographic.place.dataSetId:30caed2f-454e-44be-b5cc-26bb5c0110ca}")
	private String topographicPlaceDataSetId;


	@Autowired
	private KartverketService kartverketService;

	private static final String FORMAT_GEO_JSON = "geoJSON";

	@Override
	public void configure() throws Exception {
		super.configure();

		from("quartz2://marduk/topographicPlaceDownload?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
				.log(LoggingLevel.INFO, "Quartz triggers download of topographic place information.")
				.to("activemq:queue:TopographicPlaceDownloadQueue")
				.routeId("topographic-place-download-quartz");

		singletonFrom("activemq:queue:TopographicPlaceDownloadQueue?transacted=true&messageListenerContainerFactoryRef=batchListenerContainerFactory")
				.autoStartup("{{kartverket.topographic.place.download.autoStartup:false}}")
				.transacted()
				.log(LoggingLevel.INFO, "Start downloading topographic place information")
				.to("direct:transferAdministrativeUnitsFile")
				.to("direct:transferTopographicPlacesFilesPerFylke")
				.log(LoggingLevel.INFO, "Finished downloading topographic place information")
				.setBody(constant(null))
				.to("activemq:queue:TopographicPlaceTiamatUpdateQueue")
				.end()
				.routeId("topographic-place-download");


		from("direct:transferTopographicPlacesFilesPerFylke")
				.log(LoggingLevel.INFO, "Downloading administrative units")
				.process(e -> kartverketService.downloadFiles(topographicPlaceDataSetId, FORMAT_GEO_JSON))
				.log(LoggingLevel.INFO, "Uploaded administrative units")
				.routeId("topographic-place-per-fylke-to-blobstore");


		from("direct:transferAdministrativeUnitsFile")
				.log(LoggingLevel.INFO, "Downloading administrative units")
				.process(e -> kartverketService.downloadFiles(administrativeUnitsDataSetId, null))
				.log(LoggingLevel.INFO, "Uploaded administrative units")
				.routeId("administrative-units-to-blobstore");

	}

}
