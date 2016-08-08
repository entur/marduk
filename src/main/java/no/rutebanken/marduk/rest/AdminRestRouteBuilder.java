package no.rutebanken.marduk.rest;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

import java.util.Collections;

import org.apache.camel.LoggingLevel;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.model.rest.RestPropertyDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;

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
	    		.param().required(Boolean.TRUE).name("fileHandle").type(RestParamType.query).description("S3 file path of file to reimport").endParam()
	    		.route()
	    		.removeHeaders("CamelHttp*")
	    		.setHeader(PROVIDER_ID,header("providerId"))
            	.setBody(header("fileHandle"))
            	.split().tokenize(",")
            	.setHeader(FILE_HANDLE,body())
			    .setHeader(CORRELATION_ID, constant(System.currentTimeMillis()))
	    		.log(LoggingLevel.INFO,correlation()+"Chouette start import fileHandle=${header.fileHandle}")

                .process(e -> {
                	String fileNameForStatusLogging = e.getIn().getBody(String.class);
                	fileNameForStatusLogging = fileNameForStatusLogging.replaceFirst("inbound/received/.*/", "");
                	fileNameForStatusLogging = "reimport-"+fileNameForStatusLogging;
                	e.getIn().setHeader(Constants.FILE_NAME, fileNameForStatusLogging);
                })
            	.setBody(constant(null))
			   
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
        	.post("/{providerId}/export")
		    	.route()
	    		.setHeader(PROVIDER_ID,header("providerId"))
	    		.log(LoggingLevel.INFO,correlation()+"Chouette start export")
	    		.removeHeaders("CamelHttp*")
	    		.setHeader(PROVIDER_ID,header("providerId"))
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
}


