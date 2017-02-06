package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.chouette.json.Parameters;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import no.rutebanken.marduk.routes.status.Status;
import no.rutebanken.marduk.routes.status.Status.Action;
import no.rutebanken.marduk.routes.status.Status.State;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.Utils.getLastPathElementOfUrl;

/**
 * Exports files from Chouette
 */
@Component
public class ChouetteExportRouteBuilder extends AbstractChouetteRouteBuilder {

	@Value("${chouette.url}")
	private String chouetteUrl;

	@Value("${chouette.export.days.forward:365}")
	private int daysForward;

	@Value("${chouette.export.days.back:365}")
	private int daysBack;

	@Override
	public void configure() throws Exception {
		super.configure();

		from("activemq:queue:ChouetteExportQueue?transacted=true").streamCaching()
				.transacted()
				.log(LoggingLevel.INFO, getClass().getName(), "Starting Chouette export for provider with id ${header." + PROVIDER_ID + "}")
				.process(e -> {
					// Add correlation id only if missing
					e.getIn().setHeader(Constants.CORRELATION_ID, e.getIn().getHeader(Constants.CORRELATION_ID, UUID.randomUUID().toString()));
					e.getIn().removeHeader(Constants.CHOUETTE_JOB_ID);
				})
				.process(e -> Status.builder(e).action(Action.EXPORT).state(State.PENDING).build())
				.to("direct:updateStatus")

				.process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
				.process(e -> e.getIn().setHeader(JSON_PART, Parameters.getGtfsExportParameters(getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class))))) //Using header to addToExchange json data
				.log(LoggingLevel.INFO, correlation() + "Creating multipart request")
				.to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
				.process(e -> toGenericChouetteMultipart(e))
				.to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
				.setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"))
				.to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
				.toD(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/exporter/gtfs")
				.to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
				.process(e -> {
					e.getIn().setHeader(CHOUETTE_JOB_STATUS_URL, e.getIn().getHeader("Location").toString().replaceFirst("http", "http4"));
					e.getIn().setHeader(Constants.CHOUETTE_JOB_ID, getLastPathElementOfUrl(e.getIn().getHeader("Location", String.class)));
				})
				.setHeader(Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION, constant("direct:processExportResult"))
				.setHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE, constant(Action.EXPORT.name()))
				.to("activemq:queue:ChouettePollStatusQueue")
				.routeId("chouette-send-export-job");


		from("direct:processExportResult")
				.to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
				.choice()
				.when(simple("${header.action_report_result} == 'OK'"))
				.log(LoggingLevel.INFO, correlation() + "Export ok")
				.log(LoggingLevel.DEBUG, correlation() + "Calling url ${header.data_url}")
				.removeHeaders("Camel*")
				.setBody(simple(""))
				.setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
				.toD("${header.data_url}")
				.to("direct:addGtfsFeedInfo")
				.setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_OUTBOUND + "gtfs/${header." + CHOUETTE_REFERENTIAL + "}-" + Constants.CURRENT_AGGREGATED_GTFS_FILENAME))
				.to("direct:uploadBlob")
				.to("activemq:queue:OtpGraphQueue")
				.process(e -> Status.builder(e).action(Action.EXPORT).state(State.OK).build())
				.when(simple("${header.action_report_result} == 'NOK'"))
				.log(LoggingLevel.WARN, correlation() + "Export failed")
				.process(e -> Status.builder(e).action(Action.EXPORT).state(State.FAILED).build())
				.otherwise()
				.log(LoggingLevel.ERROR, correlation() + "Something went wrong on export")
				.process(e -> Status.builder(e).action(Action.EXPORT).state(State.FAILED).build())
				.end()
				.to("direct:updateStatus")
				.process(e -> {
					e.getIn().removeHeader(Constants.CHOUETTE_JOB_ID);
					Status.builder(e).action(Action.BUILD_GRAPH).state(State.PENDING).build();
				}).to("direct:updateStatus")
				.routeId("chouette-process-export-status");

		from("direct:addGtfsFeedInfo")
				.log(LoggingLevel.INFO, correlation() + "Adding feed_info.txt to GTFS file")
				.process(e -> {
					// Add feed info
					String feedInfoContent = "feed_id,feed_publisher_name,feed_publisher_url,feed_lang\nRB,Rutebanken,http://www.rutebanken.org,no";

					File tmpFolder = new File(System.getProperty("java.io.tmpdir"));
					File tmpFolder2 = new File(tmpFolder, UUID.randomUUID().toString());
					tmpFolder2.mkdirs();
					File feedInfoFile = new File(tmpFolder2, "feed_info.txt");

					PrintWriter writer = new PrintWriter(feedInfoFile);
					writer.write(feedInfoContent);
					writer.close();

					e.getIn().setBody(ZipFileUtils.addFilesToZip(e.getIn().getBody(InputStream.class), new File[]{feedInfoFile}));
					feedInfoFile.delete();
					tmpFolder2.delete();
				})
				.routeId("chouette-process-export-gtfs-feedinfo");
	}


}


