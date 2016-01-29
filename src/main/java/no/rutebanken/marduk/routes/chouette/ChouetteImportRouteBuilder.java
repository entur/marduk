package no.rutebanken.marduk.routes.chouette;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import no.rutebanken.marduk.management.ChouetteInfo;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.chouette.json.ActionReportWrapper;
import no.rutebanken.marduk.routes.chouette.json.ImportParameters;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Optional;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.routes.chouette.json.JobResponse.Status.*;
import static no.rutebanken.marduk.routes.status.Status.Action;
import static no.rutebanken.marduk.routes.status.Status.State;

/**
 * Submits files to Chouette
 */
@Component
public class ChouetteImportRouteBuilder extends BaseRouteBuilder {

    private int maxRetries = 100;    //TODO config
    private long retryDelay = 10 * 1000;     //TODO config

    @Value("${chouette.url}")
    private String chouetteUrl;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("activemq:queue:ChouetteImportQueue").streamCaching()
                .log(LoggingLevel.DEBUG, getClass().getName(), "Provider: ${header." + PROVIDER_ID + "}")
                .process(e -> addStatus(e, Action.IMPORT, State.STARTED))
                .to("activemq:queue:ExternalProviderStatus")
                .to("direct:getBlob")
                .process(e -> e.getIn().setHeader(CHOUETTE_PREFIX, getProviderRepository().getProviderById(e.getIn().getHeader(PROVIDER_ID, Long.class)).getChouetteInfo().getPrefix()))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .to("direct:addJson");

        from("direct:addJson")
                .process(e -> e.getIn().setHeader(JSON_PART, getJsonFileContent(e.getIn().getHeader(PROVIDER_ID, Long.class)))) //Using header to add json data
                .to("direct:sendJobRequest");

        from("direct:sendJobRequest")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Creating multipart request")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> toMultipart(e))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .toD(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_PREFIX + "}/importer/gtfs")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> {
                    e.setProperty("url", e.getIn().getHeader("Location").toString().replaceFirst("http", "http4"));
                })
                .to("direct:pollJobStatus");


        from("direct:pollJobStatus")
                .setProperty("loop_counter", constant(0))
                .setHeader("loop", constant("direct:sendJobStatusRequest"))
                .dynamicRouter().header("loop")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Done looping ${property.loop_counter} times")
                .to("direct:jobStatusDone");


        from("direct:sendJobStatusRequest").streamCaching()
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
                        e.getIn().setHeader("loop", "direct:sendDelayedJobStatusRequest");
                    }
                    e.getIn().setHeader("current_status", response.status.name());
                    e.setProperty("loop_counter", e.getProperty("loop_counter", Integer.class) + 1);
                })
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true");


        from("direct:sendDelayedJobStatusRequest").log(LoggingLevel.DEBUG, getClass().getName(), "Sleeping " + retryDelay + " ms...").delayer(retryDelay).to("direct:sendJobStatusRequest");

        from("direct:jobStatusDone").streamCaching()
                .log(LoggingLevel.DEBUG, getClass().getName(), "Exited retry loop with status ${header.current_status}")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .choice()
                .when(simple("${header.current_status} == '" + SCHEDULED + "' || ${header.current_status} == '" + STARTED + "'"))
                    .log(LoggingLevel.WARN, getClass().getName(), "Timed out with state ${header.current_status}")
                .when(simple("${header.current_status} == '" + ABORTED + "' || ${header.current_status} == '" + CANCELED + "'"))
                    .log(LoggingLevel.WARN, getClass().getName(), "Import ended in state ${header.current_status}") //TODO Does this occur?
                .end()
                .process(e -> {
                    JobResponse response = e.getIn().getBody(JobResponse.class);
                    Optional<String> actionReportUrlOptional = response.links.stream().filter(li -> "action_report".equals(li.rel)).findFirst().map(li -> li.href.replaceFirst("http", "http4"));
                    e.setProperty("url", actionReportUrlOptional.orElseThrow(() -> new IllegalArgumentException("No URL found for action report.")));
                })
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Calling url ${property.url}")
                .removeHeaders("Camel*")
                .setBody(simple(""))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .toD("${property.url}")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .unmarshal().json(JsonLibrary.Jackson, ActionReportWrapper.class)
                .process(e -> {
                    e.setProperty("action_report_result", e.getIn().getBody(ActionReportWrapper.class).actionReport.result);
                })
                .to("direct:processActionReportResult");

        from("direct:processActionReportResult")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .setHeader(Exchange.FILE_NAME, exchangeProperty(Exchange.FILE_NAME))
                .choice()
                .when(simple("${property.action_report_result} == 'OK'"))
                .log(LoggingLevel.INFO, getClass().getName(), "OK, triggering export.")
                    .to("activemq:queue:ChouetteGtfsExportQueue")
                    .process(e -> addStatus(e, Action.IMPORT, State.OK))
                .when(simple("${property.action_report_result} == 'NOK'"))
                    .log(LoggingLevel.WARN, getClass().getName(), "NOK")
                    .process(e -> addStatus(e, Action.IMPORT, State.FAILED))
                .otherwise()
                    .log(LoggingLevel.WARN, getClass().getName(), "Something went wrong")
                    .process(e -> addStatus(e, Action.IMPORT, State.FAILED))
                .end()
                .log(LoggingLevel.DEBUG, getClass().getName(), "Import done")
                .to("activemq:queue:ExternalProviderStatus");

    }

    void toMultipart(Exchange exchange) {
        String fileName = exchange.getIn().getHeader(FILE_HANDLE, String.class);
        if (Strings.isNullOrEmpty(fileName)) {
            throw new IllegalArgumentException("No file name");
        }

        String jsonPart = exchange.getIn().getHeader(JSON_PART, String.class);
        if (Strings.isNullOrEmpty(jsonPart)) {
            throw new IllegalArgumentException("No json data");
        }

        InputStream inputStream = exchange.getIn().getBody(InputStream.class);
        if (inputStream == null) {
            throw new IllegalArgumentException("No data");
        }

        MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
        entityBuilder.addBinaryBody("parameters", exchange.getIn().getHeader(JSON_PART, String.class).getBytes(), ContentType.DEFAULT_BINARY, "parameters.json");
        entityBuilder.addBinaryBody("feed", inputStream, ContentType.DEFAULT_BINARY, fileName);

        exchange.getOut().setBody(entityBuilder.build());
        exchange.getOut().setHeaders(exchange.getIn().getHeaders());
    }


    String getJsonFileContent(Long providerId) {
        try {
            ChouetteInfo chouetteInfo = getProviderRepository().getProviderById(providerId).getChouetteInfo();
            ImportParameters.GtfsImport gtfsImport = new ImportParameters.GtfsImport("import", chouetteInfo.getPrefix(), chouetteInfo.getDataSpace(), chouetteInfo.getOrganisation(), chouetteInfo.getUser());
            ImportParameters.Parameters parameters = new ImportParameters.Parameters(gtfsImport);
            ImportParameters importParameters = new ImportParameters(parameters);
            ObjectMapper mapper = new ObjectMapper();
            StringWriter writer = new StringWriter();
            mapper.writeValue(writer, importParameters);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}


