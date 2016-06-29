package no.rutebanken.marduk.rest;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

import org.apache.camel.model.rest.RestParamType;
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
        .host("localhost")
        .port(8080)
        .apiContextPath("/api-doc")
        .apiProperty("api.title", "Marduk Admin API").apiProperty("api.version", "1.0")
        // and enable CORS
        .apiProperty("cors", "true")
        .contextPath("/admin");
        
        rest("/services/chouette")
	    	.get("/{providerId}/import")
	    		.param().required(Boolean.TRUE).name("fileHandle").type(RestParamType.query).description("S3 file path of file to reimport").endParam()
	    		.route()
			    .setHeader(PROVIDER_ID,header("providerId"))
			    .setHeader(FILE_HANDLE,header("fileHandle"))
			    .to("activemq:queue:ChouetteImportQueue")
			    .routeId("admin-chouette-import")
			    .endRest()
        	.get("/{providerId}/files")
	    		.route()
			  //  .process(getProviderRepository().getProvider(id)) "inbound/received/" + provider.chouetteInfo.referential
	    		.setHeader(PROVIDER_ID,header("providerId"))
			    .to("direct:listBlobs")
			    .routeId("admin-chouette-import-list")
			    .endRest()
        	.get("/{providerId}/export")
		    	.route()
			    .setHeader(PROVIDER_ID,header("providerId"))
		    	.to("activemq:queue:ChouetteExportQueue")
			    .routeId("admin-chouette-export")
		    	.endRest();
    	
        rest("/services/graph")
	    	.get("/build")
	    		.route()
	    		.setBody(simple(""))
			    .to("activemq:queue:OtpGraphQueue")
			    .routeId("admin-build-graph")
			    .endRest();
    	
    }
}


