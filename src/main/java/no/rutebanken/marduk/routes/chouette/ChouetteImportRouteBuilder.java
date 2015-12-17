package no.rutebanken.marduk.routes.chouette;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.chouette.json.ActionReportWrapper;
import no.rutebanken.marduk.routes.chouette.json.ImportParameters;
import no.rutebanken.marduk.routes.chouette.json.ImportResponse;
import org.apache.camel.Exchange;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Optional;

/**
 * Submits files to Chouette
 */
@Component
public class ChouetteImportRouteBuilder extends BaseRouteBuilder {

    private int maxRetries = 10;    //TODO config
    private long delay = 10000;     //TODO config

    @Override
    public void configure() throws Exception {
        super.configure();

        from("activemq:queue:ChouetteImportQueue").streamCaching()
                //Assuming we have split files according to provider/agency at this point
                //get chouette referential for provider/agency or sftp user?   TODO this is input for storage part. Each flow should have a reference to admin data.
                .to("direct:getBlob")
                .to("log:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .to("direct:addJson");

        from("direct:addJson")
//                    .setProperty("referential", simple("tds"))    //TODO get this from somewhere
                .setHeader("referential", simple("tds"))     //TODO get this from somewhere
                .setHeader("jsonPart", simple(getJsonFileContent("tds"))) //Using header to add json data     //TODO get this from somewhere
                .to("direct:sendRequest");

        from("direct:sendRequest")
                .log("Creating multipart request")
                .to("log:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(e -> toMultipart(e))
                .to("log:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"))
                .to("log:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .toD("http4://chouette:8080/chouette_iev/referentials/${header.referential}/importer/gtfs")
                .to("log:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(e -> {
                    e.setProperty("url", e.getIn().getHeader("Location").toString().replaceFirst("http", "http4"));
                })
                .to("direct:processResponse");


        from("direct:processResponse").streamCaching()
                .setProperty("loop_counter", constant(0))
                .setHeader("loop", constant("direct:sendRequest2"))
                .dynamicRouter().header("loop")
                .log("Done looping ${property.loop_counter} times")
                .to("direct:processImportResult");


        from("direct:sendRequest2").streamCaching()
                .to("log:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .removeHeaders("*")
                .setBody(simple(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .toD("${property.url}")
                .to("log:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .log("Got response ${body}")
                .unmarshal().json(JsonLibrary.Jackson, ImportResponse.class)
                .to("log:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .log("Unmarshalled: ${body}")
                .process(e -> {
                    ImportResponse response = e.getIn().getBody(ImportResponse.class);
                    if (!(response.status.equals(ImportResponse.Status.SCHEDULED)
                            || response.status.equals(ImportResponse.Status.STARTED))
                            || e.getProperty("loop_counter", Integer.class) >= maxRetries) {
                        e.getIn().setHeader("loop", null);
                        e.getIn().setHeader("final_status", response.status.name());
                    } else {
                        e.getIn().setHeader("loop", "direct:sendDelayedRequest2");
                    }
                    e.setProperty("loop_counter", e.getProperty("loop_counter", Integer.class) + 1);
                })
                .to("log:" + getClass().getSimpleName() + "?showAll=true&multiline=true");


        from("direct:sendDelayedRequest2").log("Sleeping " + delay + " ms...").delayer(delay).to("direct:sendRequest2");

        from("direct:processImportResult").streamCaching()
                .log("******************** Exited retry loop with status ${header.final_status} **********************")
                  //TODO check ABORTED, CANCELED
                .to("log:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(e -> {
                    ImportResponse response = e.getIn().getBody(ImportResponse.class);
                    System.out.println("response " + response);
                    Optional<String> actionReportUrlOptional = response.links.stream().filter(li -> "action_report".equals(li.rel)).findFirst().map(li -> li.href.replaceFirst("http", "http4"));
                    e.setProperty("url", actionReportUrlOptional.orElseThrow(() -> new IllegalArgumentException("No URL found for action report.")));
                })
                .log("Calling url ${property.url}")
                .removeHeaders("*")
                .setBody(simple(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .toD("${property.url}")
                .to("log:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .unmarshal().json(JsonLibrary.Jackson, ActionReportWrapper.class)
                .process(e -> {
                    e.setProperty("action_report_result", e.getIn().getBody(ActionReportWrapper.class).actionReport.result);
                })
                .to("direct:processActionReportResult");

        from("direct:processActionReportResult")
                .to("log:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .choice()
                .when(simple("${property.action_report_result} == 'OK'"))
                .log("OK")
                .when(simple("${property.action_report_result} == 'NOK'"))
                .log("NOK")
                .otherwise()
                .log("Something went wrong")
                .end()
                .log("Done");

    }

    void toMultipart(Exchange exchange) {
        String fileName = exchange.getIn().getHeader("file_handle", String.class);
        if (Strings.isNullOrEmpty(fileName)) {
            throw new IllegalArgumentException("No file name");
        }

        String jsonPart = exchange.getIn().getHeader("jsonPart", String.class);
        if (Strings.isNullOrEmpty(jsonPart)) {
            throw new IllegalArgumentException("No json data");
        }

        InputStream inputStream = exchange.getIn().getBody(InputStream.class);
        if (inputStream == null) {
            throw new IllegalArgumentException("No data");
        }

        MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
        entityBuilder.addBinaryBody("parameters", exchange.getIn().getHeader("jsonPart", String.class).getBytes(), ContentType.DEFAULT_BINARY, "parameters.json");
        entityBuilder.addBinaryBody("feed", inputStream, ContentType.DEFAULT_BINARY, fileName);

        exchange.getOut().setBody(entityBuilder.build());
        exchange.getOut().setHeaders(exchange.getIn().getHeaders());
    }


    String getJsonFileContent(String referentialName) {
        try {
            ImportParameters.GtfsImport gtfsImport = new ImportParameters.GtfsImport("test", referentialName, "testDS", "Rutebanken", "Chouette");  //TODO configure or get from somewhere
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


