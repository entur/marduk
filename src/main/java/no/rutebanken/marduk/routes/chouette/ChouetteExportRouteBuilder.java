package no.rutebanken.marduk.routes.chouette;

import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_URL;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.JSON_PART;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.chouette.json.GtfsExportParameters;
import no.rutebanken.marduk.routes.status.Status;
import no.rutebanken.marduk.routes.status.Status.Action;
import no.rutebanken.marduk.routes.status.Status.State;

/**
 * Exports files from Chouette
 */
@Component
public class ChouetteExportRouteBuilder extends BaseRouteBuilder {

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
        		.log(LoggingLevel.INFO, getClass().getName(), "Starting Chouette export for provider with id ${header." + PROVIDER_ID + "}")
                .process(e -> { 
                	// Add correlation id only if missing
                	e.getIn().setHeader(Constants.CORRELATION_ID, e.getIn().getHeader(Constants.CORRELATION_ID,UUID.randomUUID().toString()));
                })
        		.setHeader(Constants.FILE_NAME,constant("None"))
		        .process(e -> Status.addStatus(e, Action.EXPORT, State.PENDING))
		        .to("direct:updateStatus")
        		
        		.process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .process(e -> e.getIn().setHeader(JSON_PART, getJsonFileContent(e.getIn().getHeader(PROVIDER_ID, Long.class)))) //Using header to add json data
                .log(LoggingLevel.DEBUG, getClass().getName(), "Creating multipart request")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> toMultipart(e))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .toD(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/exporter/gtfs")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> {
                    e.getIn().setHeader(CHOUETTE_JOB_STATUS_URL, e.getIn().getHeader("Location").toString().replaceFirst("http", "http4"));
                })
                .setHeader(Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION,constant("direct:processExportResult"))
        		.setHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE, constant(Action.EXPORT.name()))
                .to("activemq:queue:ChouettePollStatusQueue")
                .routeId("chouette-send-export-job");



        from("direct:processExportResult")
                .to("log:" + getClass().getName() + "?level=INFO&showAll=true&multiline=true")
                .choice()
                .when(simple("${header.action_report_result} == 'OK'"))
	                .log(LoggingLevel.INFO, getClass().getName(), "Export ok.")
	                .log(LoggingLevel.DEBUG, getClass().getName(), "Calling url ${header.data_url}")
	                .removeHeaders("Camel*")
	                .setBody(simple(""))
	                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
	                .toD("${header.data_url}")
	                .setHeader(FILE_HANDLE, simple("outbound/gtfs/${header." + CHOUETTE_REFERENTIAL + "}-" + Constants.CURRENT_AGGREGATED_GTFS_FILENAME))
	                .to("direct:uploadBlob")
	                .to("activemq:queue:OtpGraphQueue")
		            .process(e -> Status.addStatus(e, Action.EXPORT, State.OK))
                .when(simple("${header.action_report_result} == 'NOK'"))
                    .log(LoggingLevel.WARN, getClass().getName(), "Export failed.")
		            .process(e -> Status.addStatus(e, Action.EXPORT, State.FAILED))
                .otherwise()
                    .log(LoggingLevel.WARN, getClass().getName(), "Something went wrong")
		            .process(e -> Status.addStatus(e, Action.EXPORT, State.FAILED))
                .end()
		        .to("direct:updateStatus")
		        .routeId("chouette-process-export-status");
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


