package no.rutebanken.marduk.routes.chouette;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.chouette.json.ActionReportWrapper;
import no.rutebanken.marduk.routes.chouette.json.GtfsExportParameters;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.routes.chouette.json.JobResponse.Status.*;

/**
 * Exports files from Chouette
 */
@Component
public class ChouetteExportRouteBuilder extends BaseRouteBuilder {

    @Value("${chouette.max.retries:500}")
    private int maxRetries;

    @Value("${chouette.retry.delay:30000}")
    private long retryDelay;

    @Value("${chouette.url}")
    private String chouetteUrl;

    @Value("${chouette.export.days.forward:365}")
    private int daysForward;

    @Value("${chouette.export.days.back:365}")
    private int daysBack;
    
    private int consumers = 10;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("activemq:queue:ChouetteExportQueue?maxConcurrentConsumers=" + consumers)
                .log(LoggingLevel.INFO, getClass().getName(), "Starting Chouette export for provider with id ${header." + PROVIDER_ID + "}")
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .to("direct:exportAddJson");

        from("direct:exportAddJson")
                .process(e -> e.getIn().setHeader(JSON_PART, getJsonFileContent(e.getIn().getHeader(PROVIDER_ID, Long.class)))) //Using header to add json data
                .to("direct:exportSendJobRequest");

        from("direct:exportSendJobRequest")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Creating multipart request")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> toMultipart(e))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .toD(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/exporter/gtfs")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> {
                    e.setProperty("url", e.getIn().getHeader("Location").toString().replaceFirst("http", "http4"));
                })
                .to("direct:exportPollJobStatus");


        from("direct:exportPollJobStatus")
                .setProperty("loop_counter", constant(0))
                .setHeader("loop", constant("direct:exportSendJobStatusRequest"))
                .dynamicRouter().header("loop")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Done looping ${property.loop_counter} times")
                .to("direct:exportJobStatusDone");

        from("direct:exportSendJobStatusRequest").streamCaching()
                .log(LoggingLevel.DEBUG, getClass().getName(), "Loop #${property.loop_counter}")
                .removeHeaders("Camel*")
                .setBody(simple(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .toD("${property.url}")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Got response ${body}")
                .unmarshal().json(JsonLibrary.Jackson, JobResponse.class)
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Unmarshalled: ${body}")
                .process(e -> {
                    JobResponse response = e.getIn().getBody(JobResponse.class);
                    if (!(response.status.equals(SCHEDULED)
                            || response.status.equals(STARTED))
                            || e.getProperty("loop_counter", Integer.class) >= maxRetries - 1) {
                        e.getIn().setHeader("loop", null);
                    } else {
                        e.getIn().setHeader("loop", "direct:exportSendDelayedJobStatusRequest");
                    }
                    e.getIn().setHeader("current_status", response.status.name());
                    e.setProperty("loop_counter", e.getProperty("loop_counter", Integer.class) + 1);
                })
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true");

        from("direct:exportSendDelayedJobStatusRequest").log("Sleeping " + retryDelay + " ms...").delayer(retryDelay).to("direct:exportSendJobStatusRequest");

        from("direct:exportJobStatusDone").streamCaching()
                .log(LoggingLevel.DEBUG, getClass().getName(), "Exited retry loop with status ${header.current_status}")
                .choice()
                .when(simple("${header.current_status} == '" + SCHEDULED + "' || ${header.current_status} == '" + STARTED + "'"))
                    .log(LoggingLevel.WARN, getClass().getName(), "Timed out with state ${header.current_status}. Config should probably be tweaked. Stopping route.")
                    .stop()
                .when(simple("${header.current_status} == '" + ABORTED + "' || ${header.current_status} == '" + CANCELED + "'"))
                    .log(LoggingLevel.WARN, getClass().getName(), "import ended in state ${header.current_status}. Stopping route.")
                    .stop()
                .end()
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> {
                    JobResponse jobResponse = e.getIn().getBody(JobResponse.class);
                    e.setProperty("jobResponse", jobResponse);
                    Optional<String> actionReportUrlOptional = jobResponse.links.stream().filter(li -> "action_report".equals(li.rel)).findFirst().map(li -> li.href.replaceFirst("http", "http4"));
                    e.setProperty("url", actionReportUrlOptional.orElseThrow(() -> new IllegalArgumentException("No URL found for action report.")));
                })
                .log(LoggingLevel.DEBUG, getClass().getName(), "Calling url ${property.url}")
                .removeHeaders("Camel*")
                .setBody(simple(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .toD("${property.url}")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .unmarshal().json(JsonLibrary.Jackson, ActionReportWrapper.class)
                .process(e -> {
                    e.setProperty("action_report_result", e.getIn().getBody(ActionReportWrapper.class).actionReport.result);
                })
                .to("direct:exportProcessActionReportResult");

        from("direct:exportProcessActionReportResult")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .choice()
                .when(simple("${property.action_report_result} == 'OK'"))
                .log(LoggingLevel.INFO, getClass().getName(), "Export ok.")
                .process(e -> {
                    JobResponse response = e.getProperty("jobResponse", JobResponse.class);
                    Optional<String> actionReportUrlOptional = response.links.stream().filter(li -> "data".equals(li.rel)).findFirst().map(li -> li.href.replaceFirst("http", "http4"));
                    e.setProperty("url", actionReportUrlOptional.orElseThrow(() -> new IllegalArgumentException("No URL found for action report.")));
                })
                .log(LoggingLevel.DEBUG, getClass().getName(), "Calling url ${property.url}")
                .removeHeaders("Camel*")
                .setBody(simple(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .toD("${property.url}")
                .setHeader(FILE_HANDLE, simple("outbound/gtfs/${header." + CHOUETTE_REFERENTIAL + "}-" + Constants.CURRENT_AGGREGATED_GTFS_FILENAME))
                .to("direct:uploadBlob")
                .to("activemq:queue:OtpGraphQueue")
                .when(simple("${property.action_report_result} == 'NOK'"))
                    .log(LoggingLevel.WARN, getClass().getName(), "Export not ok.")
                .otherwise()
                    .log(LoggingLevel.WARN, getClass().getName(), "Something went wrong")
                .end();
    }

    void toMultipart(Exchange exchange) {
        String jsonPart = exchange.getIn().getHeader(JSON_PART, String.class);
        if (Strings.isNullOrEmpty(jsonPart)) {
            throw new IllegalArgumentException("No json data");
        }

        MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
        entityBuilder.addBinaryBody("parameters", exchange.getIn().getHeader(JSON_PART, String.class).getBytes(), ContentType.DEFAULT_BINARY, "parameters.json");

        exchange.getOut().setBody(entityBuilder.build());
        exchange.getOut().setHeaders(exchange.getIn().getHeaders());
    }

    String getJsonFileContent(Long providerId) {
        try {
            ChouetteInfo chouetteInfo = getProviderRepository().getProvider(providerId).chouetteInfo;
            GtfsExportParameters.GtfsExport gtfsExport = new GtfsExportParameters.GtfsExport("export",
                    chouetteInfo.prefix, chouetteInfo.referential, chouetteInfo.organisation, chouetteInfo.user);
            GtfsExportParameters.Parameters parameters = new GtfsExportParameters.Parameters(gtfsExport);
            GtfsExportParameters importParameters = new GtfsExportParameters(parameters);
            ObjectMapper mapper = new ObjectMapper();
            StringWriter writer = new StringWriter();
            mapper.writeValue(writer, importParameters);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    Date toDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().truncatedTo(ChronoUnit.DAYS));
    }

}


