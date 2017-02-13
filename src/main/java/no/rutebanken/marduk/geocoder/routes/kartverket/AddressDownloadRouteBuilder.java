package no.rutebanken.marduk.geocoder.routes.kartverket;


import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;

@Component
public class AddressDownloadRouteBuilder extends BaseRouteBuilder {

	/**
	 * One time per 24H on MON-FRI
	 */
	@Value("${kartverket.addresses.download.cron.schedule:0+*+*/23+?+*+MON-FRI}")
	private String cronSchedule;

	@Value("${kartverket.addresses.blobstore.subdirectory:kartverket/addresses}")
	private String blobStoreSubdirectoryForAddress;

	@Value("${kartverket.addresses.dataSetId:offisielle-adresser-utm33-csv}")
	private String addressesDataSetId;

	@Override
	public void configure() throws Exception {
		super.configure();

		from("quartz2://marduk/addressDownload?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
				.log(LoggingLevel.INFO, "Quartz triggers address download.")
				.to("activemq:queue:AddressDownloadQueue")
				.routeId("address-download-quartz");

		singletonFrom("activemq:queue:AddressDownloadQueue?transacted=true&messageListenerContainerFactoryRef=batchListenerContainerFactory")
				.autoStartup("{{kartverket.address.download.autoStartup:false}}")
				.transacted()
				.log(LoggingLevel.INFO, "Start downloading address information from mapping authority")
				.setHeader(KARTVERKET_DATASETID, constant(addressesDataSetId))
				.setHeader(FOLDER_NAME, constant(blobStoreSubdirectoryForAddress))
				.to("direct:uploadUpdatedFiles")
				.choice()
				.when(simple("${header." + CONTENT_CHANGED + "}"))
				.log(LoggingLevel.INFO, "Uploaded updated address information from mapping authority. Initiating update of Pelias")
				.setBody(constant(null))
				.to("activemq:queue:PeliasUpdateQueue")
				.otherwise()
				.log(LoggingLevel.INFO, "Finished downloading address information from mapping authority with no changes")
				.endChoice()
				.routeId("address-download");

	}


}
