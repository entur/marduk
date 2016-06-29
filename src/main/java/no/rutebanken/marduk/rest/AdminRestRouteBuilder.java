package no.rutebanken.marduk.rest;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

import org.apache.camel.model.rest.RestParamType;
import org.glassfish.grizzly.http.util.URLDecoder;
import org.springframework.stereotype.Component;

import no.rutebanken.marduk.routes.BaseRouteBuilder;

/**
 * REST interface for backdoor triggering of messages
 */
@Component
public class AdminRestRouteBuilder extends BaseRouteBuilder {

    @Override
    public void configure() throws Exception {
        super.configure();

        restConfiguration().component("netty4-http")
        .dataFormatProperty("prettyPrint", "true")
        .componentProperty("urlDecodeHeaders", "true")
        .port(8081)
        .host("localhost")
        .apiContextPath("/api-doc")
        .apiProperty("api.title", "Marduk Admin API").apiProperty("api.version", "1.0")
        // and enable CORS
        .apiProperty("cors", "true")
        .contextPath("/admin");
        
        rest("/services/{providerId}")
        	.get("/chouette/import")
        		.param().required(Boolean.TRUE).name("fileHandle").type(RestParamType.query).description("S3 file path of file to reimport").endParam()
        		.route()
			    .setHeader(PROVIDER_ID,header("providerId"))
			    .setHeader(FILE_HANDLE,header("fileHandle"))
			    .to("activemq:queue:ChouetteImportQueue")
			    .routeId("admin-chouette-import")
			    .endRest()
        	.get("/chouette/import/files")
        		.route()
			    .setHeader(PROVIDER_ID,header("providerId"))
			    .to("direct:listBlobs")
			    .routeId("admin-chouette-import-list")
			    .endRest()
        	.get("/chouette/export")
		    	.route()
			    .setHeader(PROVIDER_ID,header("providerId"))
		    	.to("activemq:queue:ChouetteExportQueue")
			    .routeId("admin-chouette-export")
		    	.endRest();
        	
    }
}


