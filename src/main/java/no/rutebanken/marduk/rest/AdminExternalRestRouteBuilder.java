/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.rest;

import jakarta.ws.rs.NotFoundException;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.UploadResult;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.rutebanken.helper.organisation.NotAuthenticatedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import static jakarta.ws.rs.core.MediaType.MULTIPART_FORM_DATA;
import static no.rutebanken.marduk.Constants.*;

/**
 * API endpoints for managing the transit data import pipeline.
 * These endpoints are intended to be used by machine-to-machine clients.
 * See {@link AdminRestRouteBuilder} for the internal API used to interact with front-ends.
 */
@Component
public class AdminExternalRestRouteBuilder extends BaseRouteBuilder {


    private static final String JSON = "application/json";
    private static final String X_OCTET_STREAM = "application/x-octet-stream";
    private static final String PLAIN = "text/plain";
    private static final String OPENAPI_DATA_TYPE_STRING = "string";

    @Override
    public void configure() throws Exception {
        super.configure();

        onException(AccessDeniedException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(403))
                .setHeader(Exchange.CONTENT_TYPE, constant(PLAIN))
                .transform(exceptionMessage());

        onException(NotAuthenticatedException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
                .setHeader(Exchange.CONTENT_TYPE, constant(PLAIN))
                .transform(exceptionMessage());

        onException(NotFoundException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                .setHeader(Exchange.CONTENT_TYPE, constant(PLAIN))
                .transform(exceptionMessage());

        rest("/timetable-management")

                .post("/datasets/{codespace}")
                .description("Upload a NeTEx dataset")
                .param().name("codespace").type(RestParamType.path).description("Codespace of the NeTEx dataset").dataType(OPENAPI_DATA_TYPE_STRING).endParam()
                .consumes(MULTIPART_FORM_DATA)
                .produces(JSON)
                .outType(UploadResult.class)
                .bindingMode(RestBindingMode.off)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(404).message("Unknown codespace").endResponseMessage()
                .responseMessage().code(500).message("Internal server error").endResponseMessage()
                .routeId("rest-admin-external-upload-file")
                .to("direct:adminExternalUploadFile")

                .post("/flex-datasets/{codespace}")
                .description("Upload a NeTEx dataset containing flexible transport data")
                .param().name("codespace").type(RestParamType.path).description("Codespace of the NeTEx flex dataset").dataType(OPENAPI_DATA_TYPE_STRING).endParam()
                .consumes(MULTIPART_FORM_DATA)
                .produces(JSON)
                .outType(UploadResult.class)
                .bindingMode(RestBindingMode.off)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(404).message("Unknown codespace").endResponseMessage()
                .responseMessage().code(500).message("Internal server error").endResponseMessage()
                .routeId("rest-admin-external-upload-flex-file")
                .to("direct:adminExternalUploadFlexFile")

                .get("/datasets/{codespace}/filtered")
                .description("Download a NeTEx dataset with private data. Expired data and unsupported NeTEx entities are filtered out")
                .param().name("codespace").type(RestParamType.path).description("Codespace of the NeTEx dataset").dataType(OPENAPI_DATA_TYPE_STRING).endParam()
                .consumes(PLAIN)
                .produces(X_OCTET_STREAM)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(404).message("Unknown codespace").endResponseMessage()
                .responseMessage().code(500).message("Internal server error").endResponseMessage()
                .routeId("rest-admin-external-download-file")
                .to("direct:adminExternalDownloadPrivateDataset")

                .get("/openapi.json")
                .produces(JSON)
                .apiDocs(false)
                .bindingMode(RestBindingMode.off)
                .to("language:simple:resource:classpath:openapi/timetable-management/openapi.json");

        from("direct:adminExternalUploadFile")
                .streamCaching()
                .process(this::setNewCorrelationId)
                .setHeader(CHOUETTE_REFERENTIAL, header("codespace"))
                .log(LoggingLevel.INFO, correlation() + "Received file from provider ${header.codespace} through the HTTP endpoint")
                .to("direct:validateReferential")
                .process(e -> e.getIn().setHeader(PROVIDER_ID, getProviderRepository().getProviderId(e.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class))))
                .to("direct:authorizeEditorRequest")
                .log(LoggingLevel.INFO, correlation() + "Authorization OK for HTTP endpoint, uploading files and starting import pipeline")
                .process(this::removeAllCamelHttpHeaders)
                .setHeader(FILE_APPLY_DUPLICATES_FILTER, simple("${properties:duplicate.filter.rest:true}", Boolean.class))
                .to("direct:uploadFilesAndStartImport")
                .process(e -> e.getIn().setBody(new UploadResult(
                        e.getIn().getHeader(Constants.CORRELATION_ID, String.class)))
                )
                .marshal().json()
                .routeId("admin-external-upload-file")
                .autoStartup("{{netex.import.http.autoStartup:true}}");

        from("direct:adminExternalUploadFlexFile")
                .streamCaching()
                .process(this::setNewCorrelationId)
                .setHeader(CHOUETTE_REFERENTIAL, header("codespace"))
                .log(LoggingLevel.INFO, correlation() + "Received flex file from provider ${header.codespace} through the HTTP endpoint")
                .to("direct:validateReferential")
                .process(e -> e.getIn().setHeader(PROVIDER_ID, getProviderRepository().getProviderId(e.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class))))
                .to("direct:authorizeEditorRequest")
                .log(LoggingLevel.INFO, correlation() + "Authorization OK for HTTP endpoint, uploading flex files and starting import pipeline")
                .process(this::removeAllCamelHttpHeaders)
                .setHeader(IMPORT_TYPE, constant(IMPORT_TYPE_NETEX_FLEX))
                .setHeader(FILE_APPLY_DUPLICATES_FILTER, simple("${properties:duplicate.filter.rest:true}", Boolean.class))
                .to("direct:uploadFilesAndStartImport")
                .process(e -> e.getIn().setBody(new UploadResult(
                        e.getIn().getHeader(Constants.CORRELATION_ID, String.class)))
                )
                .marshal().json()
                .routeId("admin-external-upload-flex-file")
                .autoStartup("{{netex.import.http.autoStartup:true}}");

        from("direct:adminExternalDownloadPrivateDataset")
                .process(this::setNewCorrelationId)
                .setHeader(CHOUETTE_REFERENTIAL, header("codespace"))
                .log(LoggingLevel.INFO, correlation() + "Received Blocks download request for provider ${header." + CHOUETTE_REFERENTIAL + "} through the HTTP endpoint")
                .to("direct:validateReferential")
                .process(e -> e.getIn().setHeader(PROVIDER_ID, getProviderRepository().getProviderId(e.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class))))
                .to("direct:authorizeBlocksDownloadRequest")
                .process(e -> e.getIn().setHeader(FILE_HANDLE, Constants.BLOBSTORE_PATH_NETEX_BLOCKS_EXPORT
                        + "rb_" + e.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class).toLowerCase()
                        + "-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME))
                .log(LoggingLevel.INFO, correlation() + "Downloading NeTEx dataset with blocks: ${header." + FILE_HANDLE + "}")
                .process(this::removeAllCamelHttpHeaders)
                .to("direct:getInternalBlob")
                .choice().when(simple("${body} == null")).setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404)).endChoice()
                .routeId("admin-external-download-private_dataset");

        from("direct:validateReferential")
                .validate(e -> getProviderRepository().getProviderId(e.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class)) != null)
                .predicateExceptionFactory((exchange, predicate, nodeId) -> new NotFoundException("Unknown chouette referential"))
                .id("validate-referential")
                .routeId("admin-validate-referential");

    }

}


