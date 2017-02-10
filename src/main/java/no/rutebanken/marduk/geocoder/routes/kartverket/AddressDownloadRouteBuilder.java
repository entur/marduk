package no.rutebanken.marduk.geocoder.routes.kartverket;


import no.rutebanken.marduk.domain.FileNameAndDigest;
import no.rutebanken.marduk.geocoder.services.KartverketService;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.processor.aggregate.GroupedMessageAggregationStrategy;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.KARTVERKET_DATASETID;

@Component
public class AddressDownloadRouteBuilder extends BaseRouteBuilder {

	/**
	 * One time per 24H on MON-FRI
	 */
	@Value("${kartverket.download.address.cron.schedule:0+*+*/23+?+*+MON-FRI}")
	private String cronSchedule;

	@Value("${kartverket.addresses.blobstore.subdirectory:kartverket/addresses}")
	private String blobStoreSubdirectoryForAddress;


	@Value("${kartverket.addresses.dataSetId:offisielle-adresser-utm33-csv}")
	private String addressesDataSetId;

	@Autowired
	private KartverketService kartverketService;

	@Autowired
	private IdempotentRepository digestIdempotentRepository;

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
				.to("direct:uploadUpdatedFiles")
				.log(LoggingLevel.INFO, "Finished downloading address information from mapping authority")
				.filter(e -> containsNewFile((List<DefaultMessage>) e.getIn().getBody()))
				.setBody(constant(null))
				.to("activemq:queue:TopographicPlaceTiamatUpdateQueue")
				.end()
				.routeId("address-download");

		from("direct:uploadUpdatedFiles")
				.bean("kartverketService", "downloadFiles")
				.split().body().aggregationStrategy(new GroupedMessageAggregationStrategy())
				.to("direct:uploadBlobIfUpdated")
				.end()
				.process(e -> e.setProperty("newfile", containsNewFile((List<DefaultMessage>) e.getIn().getBody())))
				.setProperty("newfile", containsNewFile())
				.routeId("upload-updated-files");

		from("direct:uploadFileIfUpdated")
				.setHeader(Exchange.FILE_NAME, simple(("${body.name}")))
				.setHeader(FILE_HANDLE, simple(blobStoreSubdirectoryForAddress + "/${body.name}"))
				.process(e -> e.getIn().setHeader("file_digest", DigestUtils.md5Hex(e.getIn().getBody(InputStream.class))))
				.idempotentConsumer(header("file_digest")).messageIdRepository(digestIdempotentRepository)
				.log(LoggingLevel.INFO, "Uploading ${header." + FILE_HANDLE + "}")
				.to("direct:uploadBlob")
				.setProperty("newfile", constant(true))
				.end()
				.routeId("upload-file-if-updated");

	}


	private boolean containsNewFile(List<DefaultMessage> messages) {
		for (DefaultMessage message : messages) {
			if (message.getExchange().getProperty("newfile", false, Boolean.class)) {
				return true;
			}
		}
		return false;
	}
}
