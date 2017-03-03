package no.rutebanken.marduk.geocoder.routes.pelias;

import com.google.common.collect.Lists;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.geocoder.routes.util.AbortRouteException;
import no.rutebanken.marduk.geocoder.routes.util.MarkContentChangedAggregationStrategy;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.processor.aggregate.UseOriginalAggregationStrategy;
import org.apache.camel.processor.validation.PredicateValidationException;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import static no.rutebanken.marduk.Constants.*;
import static org.apache.camel.builder.Builder.exceptionStackTrace;
import static org.apache.commons.io.FileUtils.deleteDirectory;

@Component
public class PeliasUpdateEsIndexRouteBuilder extends BaseRouteBuilder {

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

	@Autowired
	private PeliasUpdateStatusService updateStatusService;

	private static final String FILE_EXTENSION = "RutebankenFileExtension";
	private static final String CONVERSION_ROUTE = "RutebankenConversionRoute";
	private static final String WORKING_DIRECTORY = "RutebankenWorkingDirectory";

	@Override
	public void configure() throws Exception {
		super.configure();

		from("direct:insertElasticsearchIndexData")
				.bean(updateStatusService, "setBuilding")
				.setHeader(CONTENT_CHANGED, constant(false))
				.setHeader(Exchange.FILE_PARENT, constant(localWorkingDirectory))
				.to("direct:cleanUpLocalDirectory")
				.doTry()
				.multicast(new UseOriginalAggregationStrategy())
				.parallelProcessing()
				.stopOnException()
				.to("direct:insertAddresses", "direct:insertPlaceNames", "direct:insertTiamatData")
				.end()
				.endDoTry()
				.doCatch(AbortRouteException.class)
				.doFinally()
				.setHeader(Exchange.FILE_PARENT, constant(localWorkingDirectory))
				.to("direct:cleanUpLocalDirectory")
				.end()
				.choice()
				.when(e -> updateStatusService.getStatus() != PeliasUpdateStatusService.Status.ABORT)
				.to("direct:insertElasticsearchIndexDataCompleted")
				.otherwise()
				.log(LoggingLevel.INFO, "Pelias update aborted")
				.to("direct:insertElasticsearchIndexDataFailed")
				.end()

				.routeId("pelias-insert-index-data");

		from("direct:insertAddresses")
				.log(LoggingLevel.DEBUG, "Start inserting addresses to ES")
				.setHeader(Exchange.FILE_PARENT, simple(blobStoreSubdirectoryForKartverket + "/addresses"))
				.setHeader(WORKING_DIRECTORY, simple(localWorkingDirectory + "/addresses"))
				.setHeader(CONVERSION_ROUTE, constant("direct:convertToPeliasCommandsFromAddresses"))
				.setHeader(FILE_EXTENSION, constant("csv"))
				.to("direct:haltIfContentIsMissing")
				.log(LoggingLevel.DEBUG, "Finished inserting addresses to ES")
				.routeId("pelias-insert-addresses");

		from("direct:insertTiamatData")
				.log(LoggingLevel.DEBUG, "Start inserting Tiamat data to ES")
				.setHeader(Exchange.FILE_PARENT, simple(blobStoreSubdirectoryForTiamatExport))
				.setHeader(WORKING_DIRECTORY, simple(localWorkingDirectory + "/tiamat"))
				.setHeader(CONVERSION_ROUTE, constant("direct:convertToPeliasCommandsFromTiamat"))
				.setHeader(FILE_EXTENSION, constant("xml"))
				.to("direct:haltIfContentIsMissing")
				.log(LoggingLevel.DEBUG, "Finished inserting Tiamat data to ES")
				.routeId("pelias-insert-tiamat-data");

		from("direct:insertPlaceNames")
				.log(LoggingLevel.DEBUG, "Start inserting place names to ES")
				.setHeader(Exchange.FILE_PARENT, simple(blobStoreSubdirectoryForKartverket + "/placeNames"))
				.setHeader(WORKING_DIRECTORY, simple(localWorkingDirectory + "/placeNames"))
				.setHeader(CONVERSION_ROUTE, constant("direct:convertToPeliasCommandsFromPlaceNames"))
				.setHeader(FILE_EXTENSION, constant("geojson"))
				.to("direct:haltIfContentIsMissing")
				.log(LoggingLevel.DEBUG, "Finished inserting place names to ES")
				.routeId("pelias-insert-place-names");


		from("direct:haltIfContentIsMissing")
				.doTry()
				.to("direct:insertToPeliasFromFilesInFolder")
				.choice()
				.when(e -> updateStatusService.getStatus() != PeliasUpdateStatusService.Status.ABORT)
				.validate(header(Constants.CONTENT_CHANGED).isEqualTo(Boolean.TRUE))
				.end()

				.endDoTry()
				.doCatch(PredicateValidationException.class, MardukException.class)
				.bean(updateStatusService, "signalAbort")
				.log(LoggingLevel.ERROR, "Elasticsearch scratch index build failed for ${header." + WORKING_DIRECTORY + "}: " + exceptionMessage() + " stacktrace: " + exceptionStackTrace())
				.end()
				.routeId("pelias-insert-halt-if-content-missing");

		from("direct:insertToPeliasFromFilesInFolder")
				.bean("blobStoreService", "listBlobsInFolder")
				.split(simple("${body.files}")).stopOnException()
				.aggregationStrategy(new MarkContentChangedAggregationStrategy())
				.to("direct:haltIfAborted")
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
				.split().exchange(e -> listFiles(e)).stopOnException()
				.aggregationStrategy(new MarkContentChangedAggregationStrategy())
				.to("direct:haltIfAborted")
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
				.setHeader(Exchange.CONTENT_TYPE, constant("application/json; charset=utf-8"))
				.split().exchange(e ->
						                  Lists.partition(e.getIn().getBody(List.class), insertBatchSize)).stopOnException()
				.aggregationStrategy(new MarkContentChangedAggregationStrategy())
				.to("direct:haltIfAborted")

				.bean("elasticsearchCommandWriterService")
				.log(LoggingLevel.INFO, "Adding batch of indexes to elasticsearch for ${header." + FILE_HANDLE + "}")
				.toD(elasticsearchScratchUrl + "/_bulk")
				.setHeader(CONTENT_CHANGED, constant(true))                // TODO parse response?
				.log(LoggingLevel.INFO, "Finished adding batch of indexes to elasticsearch for ${header." + FILE_HANDLE + "}")

				.routeId("pelias-invoke-bulk-command");

		from("direct:haltIfAborted")
				.choice()
				.when(e -> updateStatusService.getStatus() == PeliasUpdateStatusService.Status.ABORT)
				.log(LoggingLevel.DEBUG, "Stopping route because status is ABORT")
				.throwException(new AbortRouteException())
				.end()
				.routeId("pelias-halt-if-aborted");
	}


	private Collection<File> listFiles(Exchange e) {
		String fileExtension = e.getIn().getHeader(FILE_EXTENSION, String.class);
		String directory = e.getIn().getHeader(WORKING_DIRECTORY, String.class);
		return FileUtils.listFiles(new File(directory), new String[]{fileExtension}, true);
	}

}
