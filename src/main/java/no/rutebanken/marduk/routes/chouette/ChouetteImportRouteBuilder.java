package no.rutebanken.marduk.routes.chouette;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_TYPE;
import static no.rutebanken.marduk.Constants.JSON_PART;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

import java.util.UUID;

import no.rutebanken.marduk.routes.chouette.json.NetexImportParameters;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.chouette.json.GtfsImportParameters;
import no.rutebanken.marduk.routes.chouette.json.NeptuneImportParameters;
import no.rutebanken.marduk.routes.chouette.json.RegtoppImportParameters;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.status.Status;
import no.rutebanken.marduk.routes.status.Status.Action;
import no.rutebanken.marduk.routes.status.Status.State;

/**
 * Submits files to Chouette
 */
@Component
public class ChouetteImportRouteBuilder extends AbstractChouetteRouteBuilder {


	@Value("${chouette.url}")
    private String chouetteUrl;

 	@Override
    public void configure() throws Exception {
        super.configure();

        
//        RedeliveryPolicy chouettePolicy = new RedeliveryPolicy();
//        chouettePolicy.setMaximumRedeliveries(3);
//        chouettePolicy.setRedeliveryDelay(30000);
//        chouettePolicy.setRetriesExhaustedLogLevel(LoggingLevel.ERROR);
//        chouettePolicy.setRetryAttemptedLogLevel(LoggingLevel.WARN);
//        chouettePolicy.setLogExhausted(true);
  
  
    

//        onException(HttpOperationFailedException.class, NoRouteToHostException.class)
//                .setHeader(Constants.FILE_NAME, exchangeProperty(Constants.FILE_NAME))
//                .process(e -> Status.addStatus(e, Action.IMPORT, State.FAILED))
//                .to("direct:updateStatus")
//                .log(LoggingLevel.ERROR,correlation()+ "Failed while importing to Chouette")
//                .to("log:" + getClass().getName() + "?level=ERROR&showAll=true&multiline=true")
//                .handled(true);


        from("activemq:queue:ChouetteCleanQueue?transacted=true&maxConcurrentConsumers=3")
		        .log(LoggingLevel.INFO,correlation()+"Starting Chouette dataspace clean")
		        .setHeader(Constants.FILE_NAME,constant("clean_repository.zip"))
	            .setProperty(Constants.FILE_NAME, header(Constants.FILE_NAME))
		        .setHeader(Constants.FILE_HANDLE,constant("clean_repository.zip"))
                .process(e ->  {
                	// Add correlation id only if missing
                	e.getIn().setHeader(Constants.CORRELATION_ID, e.getIn().getHeader(Constants.CORRELATION_ID,UUID.randomUUID().toString()));
                	Provider provider = getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class));
                	e.getIn().setHeader(FILE_TYPE, provider.chouetteInfo.dataFormat.toUpperCase());
                    e.getIn().setHeader(CHOUETTE_REFERENTIAL, provider.chouetteInfo.referential);                	
                })
                
		        .setHeader(Constants.CLEAN_REPOSITORY, constant(true))
		        .process(e -> Status.addStatus(e, Action.IMPORT, State.PENDING))
		        .to("direct:updateStatus")
	            .process(e -> {
	                if(FileType.REGTOPP.name().equals(e.getIn().getHeader(Constants.FILE_TYPE))) {
		            	e.getIn().setBody(getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip"));
	                } else if(FileType.GTFS.name().equals(e.getIn().getHeader(Constants.FILE_TYPE))){
		            	e.getIn().setBody(getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/empty_gtfs.zip"));
	                } else if(FileType.NEPTUNE.name().equals(e.getIn().getHeader(Constants.FILE_TYPE))){
		            	e.getIn().setBody(getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/empty_neptune.zip"));
	                } else {
	                	throw new RuntimeException("Only know how to clean regtopp and gtfs spaces so far, must add support for netex");
	                }
	            })
		        .to("direct:addImportParameters")
		        .routeId("chouette-clean-dataspace");


        from("activemq:queue:ChouetteImportQueue?transacted=true").streamCaching()
                .log(LoggingLevel.INFO,correlation()+ "Starting Chouette import")
                .process(e -> Status.addStatus(e, Action.IMPORT, State.PENDING))
                .to("direct:updateStatus")
                .to("direct:getBlob")
    	        .setHeader(Constants.CLEAN_REPOSITORY, constant(false))
                .process(e -> {
                	Provider provider = getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class));
					e.getIn().setHeader(CHOUETTE_REFERENTIAL, provider.chouetteInfo.referential);
					e.getIn().setHeader(Constants.ENABLE_VALIDATION, provider.chouetteInfo.enableValidation);
					
                })
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .to("direct:addImportParameters")
                .routeId("chouette-import-dataspace");

        from("direct:addImportParameters")
                .process(e -> {
                    String fileName = e.getIn().getHeader(Constants.FILE_NAME, String.class);
                    String fileType = e.getIn().getHeader(Constants.FILE_TYPE, String.class);
                    Long providerId = e.getIn().getHeader(PROVIDER_ID, Long.class);
                    boolean cleanRepository = (boolean) e.getIn().getHeader(Constants.CLEAN_REPOSITORY);
                    e.getIn().setHeader(JSON_PART, getImportParameters(fileName, fileType, providerId,cleanRepository));
                }) //Using header to add json data
                .log(LoggingLevel.DEBUG,correlation()+"import parameters: " + header(JSON_PART))
                .to("direct:sendImportJobRequest")
                .routeId("chouette-import-add-parameters");

        from("direct:sendImportJobRequest")
                .log(LoggingLevel.DEBUG,correlation()+"Creating multipart request")
                .process(e -> toImportMultipart(e))
                .setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .setProperty("chouette_url", simple(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/importer/${header." + FILE_TYPE + ".toLowerCase()}"))
                .log(LoggingLevel.DEBUG,correlation()+ "Calling Chouette with URL: ${exchangeProperty.chouette_url}")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
                // Attempt to retrigger delivery in case of errors
                .toD("${exchangeProperty.chouette_url}")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> {
                    e.getIn().setHeader(Constants.CHOUETTE_JOB_STATUS_URL,e.getIn().getHeader("Location").toString().replaceFirst("http", "http4"));
                })
                .setHeader(Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION,constant("direct:processImportResult"))
        		.setHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE, constant(Action.IMPORT.name()))
                .to("activemq:queue:ChouettePollStatusQueue")
                .routeId("chouette-send-import-job");

        
        // Start common
       

		 from("direct:processImportResult")
		        .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
		        .setBody(constant(""))
		        .choice()
				.when(PredicateBuilder.and(constant("false").isEqualTo(header(Constants.ENABLE_VALIDATION)),simple("${header.action_report_result} == 'OK'")))
		            .to("direct:checkScheduledJobsBeforeTriggeringNextAction")
		            .process(e -> Status.addStatus(e, Action.IMPORT, State.OK))
		        .when(simple("${header.action_report_result} == 'OK' and ${header.validation_report_result} == 'OK'"))
		            .to("direct:checkScheduledJobsBeforeTriggeringNextAction")
		            .process(e -> Status.addStatus(e, Action.IMPORT, State.OK))
		        .when(simple("${header.action_report_result} == 'OK' and ${header.validation_report_result} == 'NOK'"))
		        	.log(LoggingLevel.INFO,correlation()+"Import ok but validation failed")
		            .process(e -> Status.addStatus(e, Action.IMPORT, State.FAILED))
		        .when(simple("${header.action_report_result} == 'NOK'"))
		        	.choice()
		    		.when(simple("${header.action_report_result} == 'NOK' && ${header."+Constants.CLEAN_REPOSITORY+"} == true"))
		        		.log(LoggingLevel.INFO,correlation()+"Clean ok")
		        		.process(e -> Status.addStatus(e, Action.IMPORT, State.OK))
		    		.when(simple("${header.action_report_result} == 'NOK'"))
		        		.log(LoggingLevel.WARN,correlation()+"Import not ok")
		        		.process(e -> Status.addStatus(e, Action.IMPORT, State.FAILED))
		        	.endChoice()
		        .otherwise()
		            .log(LoggingLevel.ERROR,correlation()+"Something went wrong on import")
		            .process(e -> Status.addStatus(e, Action.IMPORT, State.FAILED))
		        .end()
		        .to("direct:updateStatus")
		        .routeId("chouette-process-import-status");
	
		 // Check that no other import jobs in status SCHEDULED exists for this referential. If so, do not trigger export
		from("direct:checkScheduledJobsBeforeTriggeringNextAction")
			.setProperty("job_status_url",simple("{{chouette.url}}/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/jobs?action=importer&status=SCHEDULED&status=STARTED"))
			.toD("${exchangeProperty.job_status_url}")
			.choice()
			.when().jsonpath("$.*[?(@.status == 'SCHEDULED')].status")
				.log(LoggingLevel.INFO,correlation()+"Import and validation ok, skipping next step as there are more import jobs active")
            .otherwise()
				.log(LoggingLevel.INFO,correlation()+"Import and validation ok, triggering next step.")
		        .setBody(constant(""))
		        .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
				.choice()
					.when(constant("true").isEqualTo(header(Constants.ENABLE_VALIDATION)))
						.log(LoggingLevel.INFO,correlation()+"Import ok, triggering validation")
						.to("activemq:queue:ChouetteValidationQueue")
					.when(method(getClass(),"shouldTransferData").isEqualTo(true))
						.log(LoggingLevel.INFO,correlation()+"Import ok, transfering data to next dataspace")
						.to("activemq:queue:ChouetteTransferExportQueue")
					.otherwise()
						.log(LoggingLevel.INFO,correlation()+"Import ok, triggering export")
						.to("activemq:queue:ChouetteExportQueue")
				.end()
		        
            .end()
            .routeId("chouette-process-job-list-after-import");
		
    }

    private Object getImportParameters(String fileName, String fileType, Long providerId,boolean cleanRepository) {
        if (FileType.REGTOPP.name().equals(fileType)){
            return getRegtoppImportParametersAsString(fileName, providerId, cleanRepository);
        } else if (FileType.GTFS.name().equals(fileType)) {
            return getGtfsImportParametersAsString(fileName, providerId, cleanRepository);
        } else if (FileType.NETEXPROFILE.name().equals(fileType)) {
            return getNetexImportParametersAsString(fileName, providerId, cleanRepository);
        } else if (FileType.NEPTUNE.name().equals(fileType)) {
			return getNeptuneImportParametersAsString(fileName, providerId, cleanRepository);
		} else {
            throw new IllegalArgumentException("Cannot create import parameters from file type '" + fileType + "'");
        }
    }

    String getRegtoppImportParametersAsString(String importName, Long providerId, boolean cleanRepository) {
        ChouetteInfo chouetteInfo = getProviderRepository().getProvider(providerId).chouetteInfo;
        if (!chouetteInfo.usesRegtopp()){
            throw new IllegalArgumentException("Could not get regtopp information about provider '" + providerId + "'.");
        }
        RegtoppImportParameters regtoppImportParameters = RegtoppImportParameters.create(importName, chouetteInfo.prefix,
                chouetteInfo.referential, chouetteInfo.organisation, chouetteInfo.user, chouetteInfo.regtoppVersion, chouetteInfo.regtoppCoordinateProjection,chouetteInfo.regtoppCalendarStrategy,cleanRepository,chouetteInfo.enableValidation);
        return regtoppImportParameters.toJsonString();
    }

    String getGtfsImportParametersAsString(String importName, Long providerId, boolean cleanRepository) {
        ChouetteInfo chouetteInfo = getProviderRepository().getProvider(providerId).chouetteInfo;
        GtfsImportParameters gtfsImportParameters = GtfsImportParameters.create(importName, chouetteInfo.prefix, chouetteInfo.referential, chouetteInfo.organisation, chouetteInfo.user,cleanRepository,chouetteInfo.enableValidation);
        return gtfsImportParameters.toJsonString();
    }

	String getNetexImportParametersAsString(String importName, Long providerId, boolean cleanRepository) {
		Provider provider = getProviderRepository().getProvider(providerId);
		NetexImportParameters netexImportParameters = NetexImportParameters.create(importName, provider.chouetteInfo.prefix,
				provider.name, provider.chouetteInfo.organisation, provider.chouetteInfo.user,
				cleanRepository, provider.chouetteInfo.enableValidation);
		return netexImportParameters.toJsonString();
	}

    String getNeptuneImportParametersAsString(String importName, Long providerId, boolean cleanRepository) {
		Provider provider = getProviderRepository().getProvider(providerId);
		NeptuneImportParameters neptuneImportParameters = NeptuneImportParameters.create(importName,
				provider.name, provider.chouetteInfo.organisation, provider.chouetteInfo.user,
				cleanRepository, provider.chouetteInfo.enableValidation);
		return neptuneImportParameters.toJsonString();
    }



}


