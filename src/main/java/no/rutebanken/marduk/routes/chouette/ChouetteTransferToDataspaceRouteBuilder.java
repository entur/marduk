package no.rutebanken.marduk.routes.chouette;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.JSON_PART;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

import java.io.IOException;
import java.io.StringWriter;
import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.chouette.json.NeptuneExportParameters;
import no.rutebanken.marduk.routes.chouette.json.NeptuneImportParameters;
import no.rutebanken.marduk.routes.status.Status;
import no.rutebanken.marduk.routes.status.Status.Action;
import no.rutebanken.marduk.routes.status.Status.State;

/**
 * Transfers data from one space to another in Chouette
 */
@Component
public class ChouetteTransferToDataspaceRouteBuilder extends AbstractChouetteRouteBuilder {

    @Value("${chouette.url}")
    private String chouetteUrl;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("activemq:queue:ChouetteTransferExportQueue?transacted=true").streamCaching()
        		.log(LoggingLevel.INFO, getClass().getName(), "Starting Chouette transfer for provider with id ${header." + PROVIDER_ID + "}")
                .process(e -> { 
                	// Add correlation id only if missing
                	e.getIn().setHeader(Constants.CORRELATION_ID, e.getIn().getHeader(Constants.CORRELATION_ID,UUID.randomUUID().toString()));
                	e.getIn().setHeader(Constants.FILE_NAME,"None");
                	e.getIn().setHeader(Constants.FILE_HANDLE,"transfer.zip");
                	e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential);
                	e.getIn().setHeader(JSON_PART, getExportParameters(e.getIn().getHeader(PROVIDER_ID, Long.class)));
                })
                .process(e -> Status.addStatus(e, Action.DATASPACE_TRANSFER, State.PENDING))
		        .to("direct:updateStatus")
                .log(LoggingLevel.INFO,correlation()+"Creating multipart request")
                .process(e -> toGenericChouetteMultipart(e))
                .toD(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/exporter/neptune")
                .process(e -> {
                    e.getIn().setHeader(Constants.CHOUETTE_JOB_STATUS_URL, e.getIn().getHeader("Location").toString().replaceFirst("http", "http4"));
                    e.getIn().setHeader(Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION,"direct:processTransferExportResult");
                    e.getIn().setHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE, Action.DATASPACE_TRANSFER.name());
                    })
		        .process(e -> Status.addStatus(e, Action.DATASPACE_TRANSFER, State.STARTED))
		        .to("direct:updateStatus")
                .log(LoggingLevel.INFO,correlation()+"Sending transfer export to poll job status")
                .to("log:" + getClass().getName() + "?level=INFO&showAll=true&multiline=true")
                .to("activemq:queue:ChouettePollStatusQueue")
                .routeId("chouette-send-transfer-export-job");



        from("direct:processTransferExportResult")
        		.log(LoggingLevel.INFO,correlation()+"Transfer export to poll job completed")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .choice()
                .when(simple("${header.action_report_result} == 'OK'"))
	                .log(LoggingLevel.INFO,correlation()+"Export for transfer ok")
	                .log(LoggingLevel.DEBUG,correlation()+"Calling url ${header.data_url}")
	                .removeHeaders("Camel*")
	                .setBody(simple(""))
	                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
	                .toD("${header.data_url}")
	                // Create new import
	                .process(e -> {
	                	Provider currentProvider = getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class));
	                	Provider destinationProvider = getProviderRepository().getProvider(currentProvider.chouetteInfo.migrateDataToProvider);
	        			e.getIn().setHeader(CHOUETTE_REFERENTIAL, destinationProvider.chouetteInfo.referential);
	                    e.getIn().setHeader(JSON_PART, getImportParameters("transfer.zip",  currentProvider.chouetteInfo.migrateDataToProvider,true));
	                }) //Using header to add json data
	                .removeHeaders("Camel*")
	                .process(e -> toImportMultipart(e))
	                .to("log:" + getClass().getName() + "?level=INFO&showAll=true&multiline=true")
	                .setProperty("chouette_url", simple(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/importer/neptune"))
	                .log(LoggingLevel.INFO,correlation()+ "Calling Chouette with URL: ${exchangeProperty.chouette_url}")
	                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
	                .toD("${exchangeProperty.chouette_url}")
	                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
	                .process(e -> {
	                    e.getIn().setHeader(Constants.CHOUETTE_JOB_STATUS_URL,e.getIn().getHeader("Location").toString().replaceFirst("http", "http4"));
	                    e.getIn().setHeader(Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION,"direct:processTransferImportResult");
	                    e.getIn().setHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE, Action.DATASPACE_TRANSFER.name());
	                })
	                .to("activemq:queue:ChouettePollStatusQueue")
                .when(simple("${header.action_report_result} == 'NOK'"))
                    .log(LoggingLevel.WARN,correlation()+"Export for transfer failed")
		            .process(e -> Status.addStatus(e, Action.DATASPACE_TRANSFER, State.FAILED))
			        .to("direct:updateStatus")
			        .stop()
                .otherwise()
                    .log(LoggingLevel.ERROR,correlation()+"Something went wrong on export for transfer")
		            .process(e -> Status.addStatus(e, Action.DATASPACE_TRANSFER, State.FAILED))
			        .to("direct:updateStatus")
			        .stop()
                .end()
                .routeId("chouette-send-transfer-import-job");
        
        

 		 from("direct:processTransferImportResult")
 		        .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
 		        .setBody(constant(""))
 		        .choice()
// 				.when(PredicateBuilder.and(constant("false").isEqualTo(header(Constants.ENABLE_VALIDATION)),simple("${header.action_report_result} == 'OK'")))
// 		            .to("direct:checkScheduledJobsBeforeTriggeringNeptuneValidation")
// 		            .process(e -> Status.addStatus(e, Action.DATASPACE_TRANSFER, State.OK))
 		        .when(simple("${header.action_report_result} == 'OK'"))
		            .process(e -> Status.addStatus(e, Action.DATASPACE_TRANSFER, State.OK))
	 		        .to("direct:updateStatus")
	                .process(e -> {
	                	// Update provider, now context switches to next provider level
	                	Provider currentProvider = getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class));
	                	e.getIn().setHeader(Constants.ORIGINAL_PROVIDER_ID,e.getIn().getHeader(Constants.ORIGINAL_PROVIDER_ID,e.getIn().getHeader(Constants.PROVIDER_ID)));
	                	e.getIn().setHeader(Constants.PROVIDER_ID, currentProvider.chouetteInfo.migrateDataToProvider);
	                }) 
 		            .to("direct:checkScheduledJobsBeforeTriggeringNeptuneValidation")
 		        .when(simple("${header.action_report_result} == 'NOK'"))
 		        	.log(LoggingLevel.INFO,correlation()+"Transfer import failed")
 		            .process(e -> Status.addStatus(e, Action.DATASPACE_TRANSFER, State.FAILED))
 	 		        .to("direct:updateStatus")
 		        .otherwise()
 		            .log(LoggingLevel.ERROR,correlation()+"Something went wrong on transfer import")
 		            .process(e -> Status.addStatus(e, Action.DATASPACE_TRANSFER, State.FAILED))
 	 		        .to("direct:updateStatus")
 		        .end()
 		        .routeId("chouette-process-transfer-import-status");
 	
 		 // Check that no other import jobs in status SCHEDULED exists for this referential. If so, do not trigger export
 		from("direct:checkScheduledJobsBeforeTriggeringNeptuneValidation")
 			.setProperty("job_status_url",simple("{{chouette.url}}/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/jobs?action=importer&status=SCHEDULED&status=STARTED")) // TODO: check on validator as well?
 			.toD("${exchangeProperty.job_status_url}")
 			.choice()
 			.when().jsonpath("$.*[?(@.status == 'SCHEDULED')].status")
 				.log(LoggingLevel.INFO,correlation()+"Transfer import ok, skipping validation as ther are more import jobs active")
             .otherwise()
 				.log(LoggingLevel.INFO,correlation()+"Transfer import ok, triggering validation.")
 		        .setBody(constant(""))
 		        
 		        .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
				.to("activemq:queue:ChouetteValidationQueue")
             .end()
             .routeId("chouette-process-job-list-after-transfer-import");

    }

	private String getImportParameters(String importName, Long providerId, boolean cleanRepository) {
		Provider provider = getProviderRepository().getProvider(providerId);
		NeptuneImportParameters regtoppImportParameters = NeptuneImportParameters.create(importName,
				provider.name, provider.chouetteInfo.organisation, provider.chouetteInfo.user,
				cleanRepository, provider.chouetteInfo.enableValidation);
		return regtoppImportParameters.toJsonString();
	}

	private String getExportParameters(Long providerId) {
		try {
			Provider provider  = getProviderRepository().getProvider(providerId);
			NeptuneExportParameters.NeptuneExport gtfsExport = new NeptuneExportParameters.NeptuneExport("export",
					provider.name, provider.chouetteInfo.organisation, provider.chouetteInfo.user);
			NeptuneExportParameters.Parameters parameters = new NeptuneExportParameters.Parameters(gtfsExport);
			NeptuneExportParameters importParameters = new NeptuneExportParameters(parameters);
			ObjectMapper mapper = new ObjectMapper();
			StringWriter writer = new StringWriter();
			mapper.writeValue(writer, importParameters);
			return writer.toString();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}


