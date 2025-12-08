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
import no.rutebanken.marduk.rest.openapi.model.UploadResult;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;

/**
 * Camel routes for managing the transit data import pipeline.
 * These routes are used by deprecated endpoints in {@link AdminRestRouteBuilder}.
 * The REST API is now served by {@link AdminExternalRestController}.
 */
@Component
public class AdminExternalRestRouteBuilder extends BaseRouteBuilder {

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:adminExternalUploadFile")
                .streamCaching()
                .process(this::setNewCorrelationId)
                .setHeader(CHOUETTE_REFERENTIAL, header("codespace"))
                .log(LoggingLevel.INFO, correlation() + "Received file from provider ${header.codespace} through the HTTP endpoint")
                .to("direct:validateReferential")
                .process(e -> e.getIn().setHeader(PROVIDER_ID, getProviderRepository().getProviderId(e.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class))))
                .to("direct:authorizeEditorRequest")
                .log(LoggingLevel.INFO, correlation() + "Authorization OK for HTTP endpoint, uploading files and starting import pipeline")
                .process(this::removeHttpHeaders)
                .setHeader(FILE_APPLY_DUPLICATES_FILTER, simple("${properties:duplicate.filter.rest:true}", Boolean.class))
                .to("direct:uploadFilesAndStartImport")
                .process(e -> e.getIn().setBody(new UploadResult()
                        .correlationId(e.getIn().getHeader(Constants.CORRELATION_ID, String.class)))
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
                .process(this::removeHttpHeaders)
                .setHeader(IMPORT_TYPE, constant(IMPORT_TYPE_NETEX_FLEX))
                .setHeader(FILE_APPLY_DUPLICATES_FILTER, simple("${properties:duplicate.filter.rest:true}", Boolean.class))
                .to("direct:uploadFilesAndStartImport")
                .process(e -> e.getIn().setBody(new UploadResult()
                        .correlationId(e.getIn().getHeader(Constants.CORRELATION_ID, String.class)))
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
                .process(this::removeHttpHeaders)
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


