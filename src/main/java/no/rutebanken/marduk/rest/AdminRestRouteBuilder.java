package no.rutebanken.marduk.rest;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.camel.Body;
import org.apache.camel.LoggingLevel;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.model.rest.RestPropertyDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.rest.S3Files.File;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.chouette.json.Status;

/**
 * REST interface for backdoor triggering of messages
 */
@Component
public class AdminRestRouteBuilder extends BaseRouteBuilder {

	@Value("${server.admin.port}")
	public int port;
	
	@Value("${server.admin.host}")
	public String host;
	
    @Override
    public void configure() throws Exception {
        super.configure();
        
        List<String> allowedStatuses = Arrays.asList(Status.values()).stream().map(Status::name).collect(Collectors.toList());

        RestPropertyDefinition corsAllowedHeaders = new RestPropertyDefinition();
        corsAllowedHeaders.setKey("Access-Control-Allow-Headers");
        corsAllowedHeaders.setValue("Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers, Authorization");

        restConfiguration().setCorsHeaders(Collections.singletonList(corsAllowedHeaders));

        restConfiguration().component("netty4-http")
        .bindingMode(RestBindingMode.json)
        .enableCORS(true)
        .dataFormatProperty("prettyPrint", "true")
        .componentProperty("urlDecodeHeaders", "true")
        .host(host)
        .port(port)
        .apiContextPath("/api-doc")
        .apiProperty("api.title", "Marduk Admin API").apiProperty("api.version", "1.0")
        .contextPath("/admin");

        rest("/services/chouette")
	    	.post("/{providerId}/import")
	    		.type(S3Files.class)
	    		.consumes("application/json")
	    		//.param().required(Boolean.TRUE).name("fileHandle").type(RestParamType.query).description("S3 file path of file to reimport").endParam()
	    		.route()
	    		.removeHeaders("CamelHttp*")
	    		.setHeader(PROVIDER_ID,header("providerId"))
	        	.split(method(ImportFilesSplitter.class,"splitFiles"))
            	.setHeader(FILE_HANDLE,body())
			    .process(e -> e.getIn().setHeader(CORRELATION_ID, UUID.randomUUID().toString()))
	    		.log(LoggingLevel.INFO,correlation()+"Chouette start import fileHandle=${body}")

                .process(e -> {
                	String fileNameForStatusLogging = e.getIn().getBody(String.class);
                	fileNameForStatusLogging = fileNameForStatusLogging.replaceFirst("inbound/received/.*/", "");
                	fileNameForStatusLogging = "reimport-"+fileNameForStatusLogging;
                	e.getIn().setHeader(Constants.FILE_NAME, fileNameForStatusLogging);
                })
            	.setBody(constant(""))
			   
                .inOnly("activemq:queue:ProcessFileQueue")
			    .routeId("admin-chouette-import")
			    .endRest()
        	.get("/{providerId}/files")
	    		.route()
	    		.setHeader(PROVIDER_ID,header("providerId"))
	    		.log(LoggingLevel.INFO,correlation()+"S3 get files")
	    		.removeHeaders("CamelHttp*")
			    .to("direct:listBlobs")
			    .routeId("admin-chouette-import-list")
			    .endRest()
        	.get("/{providerId}/jobs")
	    		.param()
	    			.required(Boolean.FALSE)
	    			.name("status")
	    			.type(RestParamType.query)
	    			.description("Chouette job statuses")
	    			.allowableValues(Arrays.asList(Status.values()).stream().map(Status::name).collect(Collectors.toList()))
	    			.endParam()
	    		.param()
	    			.required(Boolean.FALSE)
	    			.name("action")
	    			.type(RestParamType.query)
	    			.description("Chouette job types")
	    			.allowableValues("importer","exporter","validator")
	    			.endParam()
	    		.route()
	    		.setHeader(PROVIDER_ID,header("providerId"))
	    		.log(LoggingLevel.INFO,correlation()+"Get chouette jobs status=${header.status} action=${header.action}")
	    		.removeHeaders("CamelHttp*")
			    .to("direct:chouetteGetJobs")
			    .routeId("admin-chouette-list-jobs")
			    .endRest()
        	.delete("/{providerId}/jobs")
	    		.route()
	    		.setHeader(PROVIDER_ID,header("providerId"))
	    		.log(LoggingLevel.INFO,correlation()+"Cancel all chouette jobs")
	    		.removeHeaders("CamelHttp*")
			    .to("direct:chouetteCancelAllJobs")
			    .routeId("admin-chouette-cancel-all-jobs")
			    .endRest()
        	.delete("/{providerId}/jobs/{jobId}")
	    		.route()
	    		.setHeader(PROVIDER_ID,header("providerId"))
	    		.setHeader(Constants.CHOUETTE_JOB_ID,header("jobId"))
	    		.log(LoggingLevel.INFO,correlation()+"Cancel chouette job")
	    		.removeHeaders("CamelHttp*")
			    .to("direct:chouetteCancelJob")
			    .routeId("admin-chouette-cancel-job")
			    .endRest()
        	.post("/{providerId}/export")
		    	.route()
	    		.setHeader(PROVIDER_ID,header("providerId"))
	    		.log(LoggingLevel.INFO,correlation()+"Chouette start export")
	    		.removeHeaders("CamelHttp*")
		    	.inOnly("activemq:queue:ChouetteExportQueue")
			    .routeId("admin-chouette-export")
		    	.endRest()
        	.post("/{providerId}/validate")
		    	.route()
			    .setHeader(PROVIDER_ID,header("providerId"))
	    		.log(LoggingLevel.INFO,correlation()+"Chouette start validation")
	    		.removeHeaders("CamelHttp*")
		    	.inOnly("activemq:queue:ChouetteValidationQueue")
			    .routeId("admin-chouette-validate")
		    	.endRest()
	    	.post("/{providerId}/clean")
		    	.route()
			    .setHeader(PROVIDER_ID,header("providerId"))
	    		.log(LoggingLevel.INFO,correlation()+"Chouette clean dataspace")
	    		.removeHeaders("CamelHttp*")
			    .setHeader(PROVIDER_ID,header("providerId"))
		    	.inOnly("activemq:queue:ChouetteCleanQueue")
			    .routeId("admin-chouette-clean")
		    	.endRest();
    	
        rest("/services/graph")
	    	.post("/build")
	    		.route()
	    		.log(LoggingLevel.INFO,"OTP build graph")
	    		.removeHeaders("CamelHttp*")
	    		.setBody(simple(""))
			    .inOnly("activemq:queue:OtpGraphQueue")
			    .routeId("admin-build-graph")
			    .endRest();

		rest("/services/fetch")
			.post("/osm")
				.route()
	    		.log(LoggingLevel.INFO,"OSM update map data")
				.removeHeaders("CamelHttp*")
				.to("direct:considerToFetchOsmMapOverNorway")
				.routeId("admin-fetch-osm")
				.endRest();
    }
    
    public static class ImportFilesSplitter {
    	public List<String> splitFiles(@Body S3Files files) {
    		return files.getFiles().stream().map(File::getName).collect(Collectors.toList());
    	}
    }
}


