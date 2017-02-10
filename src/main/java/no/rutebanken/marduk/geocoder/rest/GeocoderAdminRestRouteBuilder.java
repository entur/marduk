package no.rutebanken.marduk.geocoder.rest;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestPropertyDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * REST interface for backdoor triggering of messages in the geocoder flows.
 */
@Component
public class GeocoderAdminRestRouteBuilder extends BaseRouteBuilder {

	@Value("${server.admin.port}")
	public String port;

	@Value("${server.admin.host}")
	public String host;

	@Override
	public void configure() throws Exception {
		super.configure();

		RestPropertyDefinition corsAllowedHeaders = new RestPropertyDefinition();
		corsAllowedHeaders.setKey("Access-Control-Allow-Headers");
		corsAllowedHeaders.setValue("Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers, Authorization");

		restConfiguration().setCorsHeaders(Collections.singletonList(corsAllowedHeaders));

		restConfiguration()
				.component("netty4-http")
				.bindingMode(RestBindingMode.json)
				.enableCORS(true)
				.dataFormatProperty("prettyPrint", "true")
				.componentProperty("urlDecodeHeaders", "true")
				.host(host)
				.port(port)
				.apiContextPath("/api-doc")
				.apiProperty("api.title", "Marduk Geocoder Admin API").apiProperty("api.version", "1.0")

				.contextPath("/geocoder/admin");

		rest("geocoder/topographicplace")
				.get("/download")
				.description("Trigger download of topographic place info from Norwegian mapping authority")
				.responseMessage().code(200).endResponseMessage()
				.responseMessage().code(500).message("Internal error").endResponseMessage()
				.route().routeId("admin-topographic-place-download")
				.removeHeaders("CamelHttp*")
				.inOnly("activemq:queue:TopographicPlaceDownloadQueue")
				.setBody(constant(null))
				.endRest()
				.get("/update")
				.description("Trigger import of topographic place info to Tiamat")
				.responseMessage().code(200).endResponseMessage()
				.responseMessage().code(500).message("Internal error").endResponseMessage()
				.route().routeId("admin-topographic-place-tiamat-update")
				.removeHeaders("CamelHttp*")
				.inOnly("activemq:queue:TiamatTopographicPlaceUpdateQueue")
				.setBody(constant(null))
				.endRest();

		rest("geocoder/address")
				.get("/download")
				.description("Trigger download of address info from Norwegian mapping authority")
				.responseMessage().code(200).endResponseMessage()
				.responseMessage().code(500).message("Internal error").endResponseMessage()
				.route().routeId("admin-address-download")
				.removeHeaders("CamelHttp*")
				.inOnly("activemq:queue:AddressDownloadQueue")
				.setBody(constant(null))
				.endRest();



		rest("geocoder/tiamat")
				.get("/export")
				.description("Trigger export from Tiamat")
				.responseMessage().code(200).endResponseMessage()
				.responseMessage().code(500).message("Internal error").endResponseMessage()
				.route().routeId("admin-tiamat-export")
				.removeHeaders("CamelHttp*")
				.inOnly("activemq:queue:TiamatExportQueue")
				.setBody(constant(null))
				.endRest();

	}
}
