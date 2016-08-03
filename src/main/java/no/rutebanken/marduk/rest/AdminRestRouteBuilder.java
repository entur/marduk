package no.rutebanken.marduk.rest;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

import java.util.Collections;

import org.apache.camel.Exchange;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.model.rest.RestPropertyDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
	    		.log("Chouette start import providerId=${header.providerId} fileHandle=${header.fileHandle}")
	    		.removeHeaders("CamelHttp*")
            	.setHeader(PROVIDER_ID,header("providerId"))
            	.setHeader(FILE_HANDLE,header("fileHandle"))
			    .setHeader(CORRELATION_ID, constant(System.currentTimeMillis()))

                .process(e -> {
                	e.getIn().setHeader(Exchange.FILE_NAME, e.getIn().getHeader("fileHandle").toString()
                    		.replaceFirst("inbound/received/", "reimported"+simple("-${date:now:yyyyMMddHHmmss}").evaluate(e, String.class)+"-"));
                })
			   
                .inOnly("activemq:queue:ProcessFileQueue")
			    .routeId("admin-chouette-import")
			    .endRest()
        	.get("/{providerId}/files")
	    		.route()
	    		.log("S3 get files for providerId=${header.providerId}")
	    		.removeHeaders("CamelHttp*")
	    		.setHeader(PROVIDER_ID,header("providerId"))
			    .to("direct:listBlobs")
				//.setBody(constant(s3FilesDummy))
			    .routeId("admin-chouette-import-list")
			    .endRest()
        	.post("/{providerId}/export")
		    	.route()
	    		.log("Chouette start export providerId=${header.providerId}")
	    		.removeHeaders("CamelHttp*")
			    .setHeader(PROVIDER_ID,header("providerId"))
		    	.inOnly("activemq:queue:ChouetteExportQueue")
			    .routeId("admin-chouette-export")
		    	.endRest()
	    	.post("/{providerId}/clean")
		    	.route()
	    		.log("Chouette clean dataspace providerId=${header.providerId}")
	    		.removeHeaders("CamelHttp*")
			    .setHeader(PROVIDER_ID,header("providerId"))
		    	.inOnly("activemq:queue:ChouetteCleanQueue")
			    .routeId("admin-chouette-clean")
		    	.endRest();
    	
        rest("/services/graph")
	    	.post("/build")
	    		.route()
	    		.log("OTP build graph")
	    		.removeHeaders("CamelHttp*")
	    		.setBody(simple(""))
			    .inOnly("activemq:queue:OtpGraphQueue")
			    .routeId("admin-build-graph")
			    .endRest();

		rest("/services/fetch")
			.post("/osm")
				.route()
	    		.log("OSM update map data")
				.removeHeaders("CamelHttp*")
				.to("direct:considerToFetchOsmMapOverNorway")
				.routeId("admin-fetch-osm")
				.endRest();
    }
}


