package no.rutebanken.marduk.geocoder.routes.pelias;

import com.google.common.collect.Lists;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import no.rutebanken.marduk.routes.status.SystemStatus;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.processor.aggregate.GroupedMessageAggregationStrategy;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.TIMESTAMP;
import static no.rutebanken.marduk.geocoder.GeoCoderConstants.KARTVERKET_ADDRESS_DOWNLOAD;
import static no.rutebanken.marduk.geocoder.GeoCoderConstants.PELIAS_UPDATE_START;

@Component
public class PeliasUpdateRouteBuilder extends BaseRouteBuilder {

	/**
	 * One time per 24H on MON-FRI
	 */
	@Value("${pelias.update.cron.schedule:0+0+23+?+*+MON-FRI}")
	private String cronSchedule;

	@Value("${babylon.url}")
	private String babylonUrl;

	@Value("${tiamat.export.blobstore.subdirectory:tiamat}")
	private String blobStoreSubdirectoryForTiamatExport;

	@Value("${kartverket.place.names.blobstore.subdirectory:kartverket}")
	private String blobStoreSubdirectoryForKartverket;

	@Value("${pelias.download.directory:files/pelias}")
	private String localWorkingDirectory;


	@Value("${pelias.insert.batch.size:10000}")
	private int insertBatchSize;

	@Override
	public void configure() throws Exception {
		super.configure();

		singletonFrom("quartz2://marduk/peliasUpdate?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
				.autoStartup("{{pelias.update.autoStartup:false}}")
				.log(LoggingLevel.INFO, "Quartz triggers download of administrative units.")
				.setBody(constant(PELIAS_UPDATE_START))
				.to("direct:geoCoderStart")
				.routeId("pelias-update-quartz");

		from(PELIAS_UPDATE_START.getEndpoint())
				.setProperty(TIMESTAMP, simple("${date:now:yyyyMMddHHmmss}"))
				.log(LoggingLevel.INFO, "Start updating Pelias")
				.process(e -> SystemStatus.builder(e).start(SystemStatus.Action.UPDATE).entity("Pelias").build()).to("direct:updateSystemStatus")

				// TODO start ES

				// TODO poll, create pelias index?
				.multicast(new GroupedMessageAggregationStrategy()).parallelProcessing().to("direct:insertAddresses", "direct:insertPlaceNames", "direct:insertTiamatData")

				// TODO build docker image

				// TODO deploy pelias

				// TODO - how do we handle files that no longer exists. Fetch bloblist in download route and delete no longer present? risky if md5 still in idempotent filter?

				// TODO detect and fail job when data is missing?

				// TODO clean up
				.setHeader(Exchange.FILE_PARENT, simple(localWorkingDirectory + "/?fileName=${property." + TIMESTAMP + "}"))
				.to("direct:cleanUpLocalDirectory")

				.to("direct:processPeliasDeployCompleted")  // TODO replace with polling after deploy
				.routeId("pelias-upload");


		from("direct:insertAddresses")
				.log(LoggingLevel.DEBUG, "Start inserting addresses to ES")
				.setHeader(Exchange.FILE_PARENT, simple(blobStoreSubdirectoryForKartverket + "/addresses"))
				.setHeader(WORKING_DIRECTORY, simple(localWorkingDirectory + "/?fileName=${property." + TIMESTAMP + "}/addresses"))
				.setHeader(CONVERSION_ROUTE, constant("direct:convertToPeliasCommandsFromAddresses"))
				.setHeader(FILE_EXTENSION, constant("csv"))
				.to("direct:insertToPeliasFromFilesInFolder")
				.log(LoggingLevel.DEBUG, "Finished inserting addresses to ES")
				.routeId("pelias-insert-addresses");

		from("direct:insertTiamatData")
				.log(LoggingLevel.DEBUG, "Start inserting Tiamat data to ES")
				.setHeader(Exchange.FILE_PARENT, simple(blobStoreSubdirectoryForTiamatExport))
				.setHeader(WORKING_DIRECTORY, simple(localWorkingDirectory + "/?fileName=${property." + TIMESTAMP + "}/tiamat"))
				.setHeader(CONVERSION_ROUTE, constant("direct:convertToPeliasCommandsFromTiamat"))
				.to("direct:insertToPeliasFromFilesInFolder")
				.log(LoggingLevel.DEBUG, "Finished inserting Tiamat data to ES")
				.routeId("pelias-insert-tiamat-data");

		from("direct:insertPlaceNames")
				.log(LoggingLevel.DEBUG, "Start inserting place names to ES")
				.setHeader(Exchange.FILE_PARENT, simple(blobStoreSubdirectoryForKartverket + "/placeNames"))
				.setHeader(WORKING_DIRECTORY, simple(localWorkingDirectory + "/?fileName=${property." + TIMESTAMP + "}/placeNames"))
				.setHeader(CONVERSION_ROUTE, constant("direct:convertToPeliasCommandsFromPlaceNames"))
				.setHeader(FILE_EXTENSION, constant("geojson"))
				.to("direct:insertToPeliasFromFilesInFolder")
				.log(LoggingLevel.DEBUG, "Finished inserting place names to ES")
				.routeId("pelias-insert-place-names");

		from("direct:insertToPeliasFromFilesInFolder")
				.bean("blobStoreService", "listBlobsInFolder")
				.split(simple("${body.files}"))
				.setHeader(FILE_HANDLE, simple("${body.name}"))
				.to("direct:getBlob")
				.to("direct:unzipIfZippedFile")
				.split().exchange(e -> listFiles(e))
				.log(LoggingLevel.INFO, "Updating indexes in pelias from file: ${body.name}")
				.toD("${header." + CONVERSION_ROUTE + "}")
				.to("direct:invokePeliasBulkCommand")
				.routeId("pelias-insert-from-folder");


		from("direct:unzipIfZippedFile")
				.choice()
				.when(header(FILE_HANDLE).endsWith(".zip"))
				.process(e -> ZipFileUtils.unzipFile(e.getIn().getBody(InputStream.class), e.getIn().getHeader(WORKING_DIRECTORY, String.class)))
				.end()
				.routeId("file-unzip-if-zipped");


		from("direct:convertToPeliasCommandsFromPlaceNames")
				.bean("placeNamesStreamToElasticsearchCommands", "transform")
				.routeId("pelias-convert-commands-place-names");

		from("direct:convertToPeliasCommandsFromAddresses")
				.bean("addressStreamToElasticSearchCommands", "transform")
				.routeId("pelias-convert-commands-from-addresses");

		from("direct:convertToPeliasCommandsFromTiamat")
				.bean("deliveryPublicationToElasticsearchCommands", "transform")
				.routeId("pelias-convert-commands-from-tiamat");


		from("direct:invokePeliasBulkCommand")
				.setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
				//	.setHeader(Exchange.HTTP_CHARACTER_ENCODING, constant("UTF-8"))
				.setHeader(Exchange.CONTENT_TYPE, constant("application/json; charset=utf-8"))
				.split().exchange(e -> Lists.partition(e.getIn().getBody(List.class), insertBatchSize))
				.bean("elasticsearchCommandWriterService")
				.log(LoggingLevel.INFO, "Adding batch of indexes to elasticsearch")
				.process(e ->
						         e.getIn())
				.to("http4:localhost:9200/_bulk")
				.log(LoggingLevel.INFO, "Finished adding batch of indexes to elasticsearch")
				// TODO parse response?
				.routeId("pelias-invoke-bulk-command");

		from("direct:processPeliasDeployCompleted")
				.log(LoggingLevel.INFO, "Finished updating pelias")
				.process(e -> SystemStatus.builder(e).state(SystemStatus.State.OK).build()).to("direct:updateSystemStatus")
				.routeId("pelias-deploy-completed");

// TODO replace with gc
		from("direct:getBlobTODO")
				.process(e ->
						         e.getIn().setBody(new FileInputStream("files/blob/" + e.getIn().getHeader(FILE_HANDLE, String.class))))
				.routeId("delete-me");

	}

	private static final String FILE_EXTENSION = "RutebankenFileExtension";
	private static final String CONVERSION_ROUTE = "RutebankenConversionRoute";
	private static final String WORKING_DIRECTORY = "RutebankenWorkingDirectory";

	private String workingDirectory(Exchange e) {
		return localWorkingDirectory + "/" + e.getProperty(TIMESTAMP);
	}

	private Collection<File> listFiles(Exchange e) {
		String fileExtension = e.getIn().getHeader(FILE_EXTENSION, String.class);
		String directory = e.getIn().getHeader(WORKING_DIRECTORY, String.class);
		return FileUtils.listFiles(new File(directory), new String[]{fileExtension}, true);
	}

}
