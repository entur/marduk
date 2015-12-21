package no.rutebanken.marduk.routes.chouette;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.chouette.json.ActionReportWrapper;
import no.rutebanken.marduk.routes.chouette.json.ExportParameters;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

import static no.rutebanken.marduk.routes.chouette.json.JobResponse.Status.*;

/**
 * Exports files from Chouette
 */
@Component
public class ChouetteExportRouteBuilder extends BaseRouteBuilder {

    private int maxRetries = 100;    //TODO config
    private long retryDelay = 30 * 1000;     //TODO config
    private int days = 14;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("activemq:queue:ChouetteGtfsExportTriggerQueue")
                .log(LoggingLevel.INFO, getClass().getName(), "Running new Chouette GTFS export.")
                .setHeader("prefix", constant("tds1"))  //TODO Get this from somewhere
                .to("direct:exportAddJson");

        from("direct:exportAddJson")
                .setHeader("jsonPart", simple(getJsonFileContent())) //Using header to add json data
                .to("direct:exportSendJobRequest");

        from("direct:exportSendJobRequest")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Creating multipart request")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> toMultipart(e))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .toD("http4://chouette:8080/chouette_iev/referentials/${header.prefix}/exporter/gtfs")
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
                .removeHeaders("*")
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
                    .log(LoggingLevel.WARN, getClass().getName(), "Timed out with state ${header.current_status}")
                .when(simple("${header.current_status} == '" + ABORTED + "' || ${header.current_status} == '" + CANCELED + "'"))
                    .log(LoggingLevel.WARN, getClass().getName(), "import ended in state ${header.current_status}") //TODO Does this occur?
                    .end()
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> {
                    JobResponse jobResponse = e.getIn().getBody(JobResponse.class);
                    e.setProperty("jobResponse", jobResponse);
                    Optional<String> actionReportUrlOptional = jobResponse.links.stream().filter(li -> "action_report".equals(li.rel)).findFirst().map(li -> li.href.replaceFirst("http", "http4"));
                    e.setProperty("url", actionReportUrlOptional.orElseThrow(() -> new IllegalArgumentException("No URL found for action report.")));
                })
                .log(LoggingLevel.DEBUG, getClass().getName(), "Calling url ${property.url}")
                .removeHeaders("*")
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
                .log(LoggingLevel.INFO, getClass().getName(), "OK")
                .process(e -> {
                    JobResponse response = e.getProperty("jobResponse", JobResponse.class);
                    Optional<String> actionReportUrlOptional = response.links.stream().filter(li -> "data".equals(li.rel)).findFirst().map(li -> li.href.replaceFirst("http", "http4"));
                    e.setProperty("url", actionReportUrlOptional.orElseThrow(() -> new IllegalArgumentException("No URL found for action report.")));
                })
                .log(LoggingLevel.DEBUG, getClass().getName(), "Calling url ${property.url}")
                .removeHeaders("*")
                .setBody(simple(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .toD("${property.url}")
                .setHeader("file_handle", simple("gtfs_export_tds1_${date:now:yyyyMMddHHmmss}.zip"))    //TODO create based on prefix
                .to("direct:uploadBlob")
                .when(simple("${property.action_report_result} == 'NOK'"))
                    .log(LoggingLevel.WARN, getClass().getName(), "NOK")
                .otherwise()
                    .log(LoggingLevel.WARN, getClass().getName(), "Something went wrong")
                .end()
                .log(LoggingLevel.DEBUG, getClass().getName(), "Done");

    }

    void toMultipart(Exchange exchange) {
        String jsonPart = exchange.getIn().getHeader("jsonPart", String.class);
        if (Strings.isNullOrEmpty(jsonPart)) {
            throw new IllegalArgumentException("No json data");
        }

        MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
        entityBuilder.addBinaryBody("parameters", exchange.getIn().getHeader("jsonPart", String.class).getBytes(), ContentType.DEFAULT_BINARY, "parameters.json");

        exchange.getOut().setBody(entityBuilder.build());
        exchange.getOut().setHeaders(exchange.getIn().getHeaders());
    }


    String getJsonFileContent() {
        try {
            ExportParameters.GtfsExport gtfsExport = new ExportParameters.GtfsExport("test", "tds1", "testDS1", "Rutebanken1", "tg@scienta.no", Date.from(Instant.now().minus(365, ChronoUnit.DAYS)), Date.from(Instant.now().plus(days, ChronoUnit.DAYS)));  //TODO configure or get from somewhere
            ExportParameters.Parameters parameters = new ExportParameters.Parameters(gtfsExport);
            ExportParameters importParameters = new ExportParameters(parameters);
            ObjectMapper mapper = new ObjectMapper();
            StringWriter writer = new StringWriter();
            mapper.writeValue(writer, importParameters);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}


