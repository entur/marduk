package no.rutebanken.marduk.geocoder.routes.pelias;

import com.google.common.collect.Lists;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.geocoder.routes.MarkContentChangedAggregationStrategy;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import no.rutebanken.marduk.routes.status.SystemStatus;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.processor.aggregate.UseOriginalAggregationStrategy;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.geocoder.GeoCoderConstants.PELIAS_UPDATE_START;
import static org.apache.commons.io.FileUtils.deleteDirectory;

@Component
public class PeliasUpdateRouteBuilder extends BaseRouteBuilder {

	/**
	 * One time per 24H on MON-FRI
	 */
	@Value("${pelias.update.cron.schedule:0+0+23+?+*+MON-FRI}")
	private String cronSchedule;

	@Value("${babylon.url}")
	private String babylonUrl;

	@Value("${elasticsearch.scratch.url:http4://es-scratch:9200}")
	private String elasticsearchScratchUrl;

	@Value("${tiamat.export.blobstore.subdirectory:tiamat}")
	private String blobStoreSubdirectoryForTiamatExport;

	@Value("${kartverket.place.names.blobstore.subdirectory:kartverket}")
	private String blobStoreSubdirectoryForKartverket;

	@Value("${pelias.download.directory:files/pelias}")
	private String localWorkingDirectory;

	@Value("${pelias.insert.batch.size:10000}")
	private int insertBatchSize;

	private static final String FILE_EXTENSION = "RutebankenFileExtension";
	private static final String CONVERSION_ROUTE = "RutebankenConversionRoute";
	private static final String WORKING_DIRECTORY = "RutebankenWorkingDirectory";

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

				.to("direct:insertElasticsearchIndexData")

				// TODO build docker image

				// TODO deploy pelias

				// TODO - how do we handle files that no longer exists. Fetch bloblist in download route and delete no longer present? risky if md5 still in idempotent filter?

				.setHeader(Exchange.FILE_PARENT, simple(localWorkingDirectory + "?fileName=${property." + TIMESTAMP + "}"))
				.to("direct:cleanUpLocalDirectory")
				.to("direct:processPeliasDeployCompleted")  // TODO replace with polling after deploy
				.routeId("pelias-upload");


		from("direct:insertElasticsearchIndexData")
				.setHeader(CONTENT_CHANGED, constant(false))
				.multicast(new UseOriginalAggregationStrategy())
				.parallelProcessing()
				.stopOnException()
				.to("direct:insertTiamatData") // TODO
				.end()
				.routeId("pelias-insert-index-data");

		from("direct:insertAddresses")
				.log(LoggingLevel.DEBUG, "Start inserting addresses to ES")
				.setHeader(Exchange.FILE_PARENT, simple(blobStoreSubdirectoryForKartverket + "/addresses"))
				.setHeader(WORKING_DIRECTORY, simple(localWorkingDirectory + "/${property." + TIMESTAMP + "}/addresses"))
				.setHeader(CONVERSION_ROUTE, constant("direct:convertToPeliasCommandsFromAddresses"))
				.setHeader(FILE_EXTENSION, constant("csv"))
				.to("direct:insertToPeliasFromFilesInFolder")
				.validate(header(Constants.CONTENT_CHANGED).isEqualTo(Boolean.TRUE))
				.log(LoggingLevel.DEBUG, "Finished inserting addresses to ES")
				.routeId("pelias-insert-addresses");

		from("direct:insertTiamatData")
				.log(LoggingLevel.DEBUG, "Start inserting Tiamat data to ES")
				.setHeader(Exchange.FILE_PARENT, simple(blobStoreSubdirectoryForTiamatExport))
				.setHeader(WORKING_DIRECTORY, simple(localWorkingDirectory + "/${property." + TIMESTAMP + "}/tiamat"))
				.setHeader(CONVERSION_ROUTE, constant("direct:convertToPeliasCommandsFromTiamat"))
				.setHeader(FILE_EXTENSION, constant("xml"))
				.to("direct:insertToPeliasFromFilesInFolder")
				.validate(header(Constants.CONTENT_CHANGED).isEqualTo(Boolean.TRUE))
				.log(LoggingLevel.DEBUG, "Finished inserting Tiamat data to ES")
				.routeId("pelias-insert-tiamat-data");

		from("direct:insertPlaceNames")
				.log(LoggingLevel.DEBUG, "Start inserting place names to ES")
				.setHeader(Exchange.FILE_PARENT, simple(blobStoreSubdirectoryForKartverket + "/placeNames"))
				.setHeader(WORKING_DIRECTORY, simple(localWorkingDirectory + "/${property." + TIMESTAMP + "}/placeNames"))
				.setHeader(CONVERSION_ROUTE, constant("direct:convertToPeliasCommandsFromPlaceNames"))
				.setHeader(FILE_EXTENSION, constant("geojson"))
				.to("direct:insertToPeliasFromFilesInFolder")
				.validate(header(Constants.CONTENT_CHANGED).isEqualTo(Boolean.TRUE))
				.log(LoggingLevel.DEBUG, "Finished inserting place names to ES")
				.routeId("pelias-insert-place-names");

		from("direct:insertToPeliasFromFilesInFolder")
				.bean("blobStoreService", "listBlobsInFolder")
				.split(simple("${body.files}"))
				.aggregationStrategy(new MarkContentChangedAggregationStrategy())
				.setHeader(FILE_HANDLE, simple("${body.name}"))
				.to("direct:getBlob")
				.choice()
				.when(header(FILE_HANDLE).endsWith(".zip"))
				.to("direct:insertToPeliasFromZipArchive")
				.otherwise()
				.log(LoggingLevel.INFO, "Updating indexes in elasticsearch from file: ${header." + FILE_HANDLE + "}")
				.toD("${header." + CONVERSION_ROUTE + "}")
				.to("direct:invokePeliasBulkCommand")
				.end()
				.routeId("pelias-insert-from-folder");


		from("direct:insertToPeliasFromZipArchive")
				.process(e -> ZipFileUtils.unzipFile(e.getIn().getBody(InputStream.class), e.getIn().getHeader(WORKING_DIRECTORY, String.class)))
				.split().exchange(e -> listFiles(e))
				.aggregationStrategy(new MarkContentChangedAggregationStrategy())
				.log(LoggingLevel.INFO, "Updating indexes in elasticsearch from file: ${body.name}")
				.toD("${header." + CONVERSION_ROUTE + "}")
				.to("direct:invokePeliasBulkCommand")
				.process(e -> deleteDirectory(new File(e.getIn().getHeader(WORKING_DIRECTORY, String.class))))
				.routeId("pelias-insert-from-zip");


		from("direct:convertToPeliasCommandsFromPlaceNames")
				.bean("placeNamesStreamToElasticsearchCommands", "transform")
				.routeId("pelias-convert-commands-place-names");

		from("direct:convertToPeliasCommandsFromAddresses")
				.bean("addressStreamToElasticSearchCommands", "transform")
				.routeId("pelias-convert-commands-from-addresses");

		from("direct:convertToPeliasCommandsFromTiamat")
				.bean("deliveryPublicationStreamToElasticsearchCommands", "transform")
				.routeId("pelias-convert-commands-from-tiamat");


		from("direct:invokePeliasBulkCommand")
				.setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
				//	.setHeader(Exchange.HTTP_CHARACTER_ENCODING, constant("UTF-8"))
				.setHeader(Exchange.CONTENT_TYPE, constant("application/json; charset=utf-8"))
				.split().exchange(e ->
						                  Lists.partition(e.getIn().getBody(List.class), insertBatchSize))
				.aggregationStrategy(new MarkContentChangedAggregationStrategy())
				.bean("elasticsearchCommandWriterService")
				.log(LoggingLevel.INFO, "Adding batch of indexes to elasticsearch for ${header." + FILE_HANDLE + "}")
				.toD(elasticsearchScratchUrl + "/_bulk")
				.setHeader(CONTENT_CHANGED, constant(true))                // TODO parse response?
				.log(LoggingLevel.INFO, "Finished adding batch of indexes to elasticsearch for ${header." + FILE_HANDLE + "}")

				.routeId("pelias-invoke-bulk-command");

		from("direct:processPeliasDeployCompleted")
				.log(LoggingLevel.INFO, "Finished updating pelias")
				.process(e -> SystemStatus.builder(e).state(SystemStatus.State.OK).build()).to("direct:updateSystemStatus")
				.routeId("pelias-deploy-completed");

	}


	private Collection<File> listFiles(Exchange e) {
		String fileExtension = e.getIn().getHeader(FILE_EXTENSION, String.class);
		String directory = e.getIn().getHeader(WORKING_DIRECTORY, String.class);
		return FileUtils.listFiles(new File(directory), new String[]{fileExtension}, true);
	}

}
