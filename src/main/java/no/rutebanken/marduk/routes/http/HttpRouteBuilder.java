package no.rutebanken.marduk.routes.http;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.security.AuthorizationClaim;
import no.rutebanken.marduk.security.AuthorizationService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.model.rest.RestPropertyDefinition;
import org.rutebanken.helper.organisation.AuthorizationConstants;
import org.rutebanken.helper.organisation.NotAuthenticatedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.util.Collections;

import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

/**
 * REST interface for backdoor triggering of messages
 */
@Component
public class HttpRouteBuilder extends BaseRouteBuilder {

    private static final String PLAIN = "text/plain";


    @Autowired
    private AuthorizationService authorizationService;

    @Override
    public void configure() throws Exception {
        super.configure();

        onException(AccessDeniedException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(403))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .transform(exceptionMessage());

        onException(NotAuthenticatedException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .transform(exceptionMessage());

        onException(NotFoundException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .transform(exceptionMessage());

        restConfiguration()
                .component("servlet")
                .contextPath("/services")
                .bindingMode(RestBindingMode.json)
                .endpointProperty("matchOnUriPrefix", "true")
                .apiContextPath("/swagger.json")
                .apiProperty("api.title", "Marduk Admin API").apiProperty("api.version", "1.0")
                .apiContextRouteId("doc-api");

        rest("/upload")
        .post("{codespace}")
        .description("Upload NeTEx file")
        .param().name("codespace").type(RestParamType.path).description("Provider Codespace").dataType("string").endParam()
        .consumes(MULTIPART_FORM_DATA)
        .produces(PLAIN)
        .bindingMode(RestBindingMode.off)
        .responseMessage().code(200).endResponseMessage()
        .responseMessage().code(500).message("Invalid codespace").endResponseMessage()
        .route()
        .streamCaching()
         .log(LoggingLevel.INFO, correlation() + "Uploading file from provider " + header("codespace"))
        .setHeader(CHOUETTE_REFERENTIAL, header("codespace"))
        .validate(e -> getProviderRepository().getProviderId(e.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class)) != null)
        .process(e -> e.getIn().setHeader(PROVIDER_ID, getProviderRepository().getProviderId(e.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class))))
        .log(LoggingLevel.INFO, correlation() + "upload files and start import pipeline")
        .removeHeaders("CamelHttp*")
        .to("direct:uploadFilesAndStartImport")
        .routeId("admin-upload-file")
        .endRest();

    }

}


