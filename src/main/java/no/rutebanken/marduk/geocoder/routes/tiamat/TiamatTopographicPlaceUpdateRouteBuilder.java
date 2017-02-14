package no.rutebanken.marduk.geocoder.routes.tiamat;


import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;

@Component
public class TiamatTopographicPlaceUpdateRouteBuilder extends BaseRouteBuilder {

	@Value("${kartverket.topographic.place.blobstore.subdirectory:kartverket/topographicPlace}")
	private String blobStoreSubdirectoryForTopographicPlaces;

	@Value("${kartverket.administrative.units.blobstore.subdirectory:kartverket/administrativeUnits}")
	private String blobStoreSubdirectoryForAdministrativeUnits;


	@Value("${tiama.topographic.place.update.cron.schedule:0+*+*/23+?+*+MON-FRI}")
	private String cronSchedule;

	@Value("${tiamat.url}")
	private String tiamatUrl;

	@Override
	public void configure() throws Exception {
		super.configure();

		singletonFrom("quartz2://marduk/tiamatTopographicPlaceUpdate?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
				.autoStartup("{{tiamat.topographic.place.update.autoStartup:false}}")
				.log(LoggingLevel.INFO, "Quartz triggers Tiamat update of topographic place info.")
				.to("activemq:queue:TiamatTopographicPlaceUpdateQueue")
				.routeId("tiamat-topographic-place-update-quartz");

		singletonFrom("activemq:queue:TiamatTopographicPlaceUpdateQueue?transacted=true&messageListenerContainerFactoryRef=batchListenerContainerFactory")
				.autoStartup("{{tiamat.topographic.place.update.autoStartup:false}}")
				.transacted()
				.log(LoggingLevel.INFO, "Start updating topographic place information in Tiamat")
//				.setHeader(Constants.JOB_ID, simple("${body.id}")) TODO import not ready
//				.setHeader(Constants.JOB_STATUS_URL, simple("${body.jobUrl}"))
				.setHeader(Constants.JOB_STATUS_ROUTING_DESTINATION, constant("direct:processTiamatTopographicPlaceUpdateCompleted"))
				.bean("blobStoreService", "listBlobs")
				.log(LoggingLevel.INFO, "Finished updating topographic place information in Tiamat")
				.end()
				.routeId("tiamat-topographic-place-update");


		from("direct:processTiamatTopographicPlaceUpdateCompleted")
				.to("activemq:queue:PeliasUpdateQueue")
				.routeId("tiamat-topographic-place-update-completed");
	}
}
