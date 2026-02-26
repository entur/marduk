package no.rutebanken.marduk.routes.experimental;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;

/**
 * Routes for integrating Servicelinker into the NeTEx processing pipeline.
 * <p>
 * When linking is enabled, this route intercepts the flow between Antu pre-validation and Ashur filtering:
 * 1. Copies the pre-validated NeTEx ZIP to the exchange bucket for Servicelinker to read
 * 2. Publishes a message to ServicelinkerInboundQueue
 * 3. Waits for the async callback on ServicelinkerStatusQueue
 * 4. On success: copies the enriched file from Servicelinker's exchange bucket to a dedicated path in the internal bucket (the original is left untouched)
 * 5. Continues to Ashur filtering: Marduk copies the enriched file from the internal bucket to the exchange bucket, which Ashur then reads
 * <p>
 * On failure, the original file remains in the internal bucket and the pipeline continues
 * to Ashur without ServiceLink enrichment (graceful degradation).
 */
@Component
public class ServicelinkerEnrichmentStatusRouteBuilder extends BaseRouteBuilder {

    private final ExperimentalImportHelpers experimentalImportHelpers;
    private final boolean linkingEnabled;

    public ServicelinkerEnrichmentStatusRouteBuilder(
        ExperimentalImportHelpers experimentalImportHelpers,
        @Value("${servicelinker.linkingEnabled:false}") boolean linkingEnabled
    ) {
        this.experimentalImportHelpers = experimentalImportHelpers;
        this.linkingEnabled = linkingEnabled;
    }

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:servicelinkerEnrichAfterPreValidation")
                .choice()
                    .when(exchange -> !linkingEnabled)
                        .log(LoggingLevel.INFO, correlation() + "Servicelinker linking is disabled, skipping enrichment")
                        .to("direct:ashurNetexFilterAfterPreValidation")
                    .when(experimentalImportHelpers::shouldSkipServicelinker)
                        .log(LoggingLevel.INFO, correlation() + "Service link modes explicitly set to empty for referential ${header." + DATASET_REFERENTIAL + "}, skipping servicelinker")
                        .to("direct:ashurNetexFilterAfterPreValidation")
                    .otherwise()
                        .log(LoggingLevel.INFO, correlation() + "Triggering Servicelinker enrichment for referential ${header." + DATASET_REFERENTIAL + "}")
                        .setHeader(TARGET_FILE_HANDLE).method(experimentalImportHelpers, "pathToNetexForServicelinker")
                        .setHeader(TARGET_CONTAINER, simple("${properties:blobstore.gcs.exchange.container.name}"))
                        .process(experimentalImportHelpers::setServiceLinkModesHeader)
                        .to("direct:copyInternalBlobToAnotherBucket")
                        .to("google-pubsub:{{marduk.pubsub.project.id}}:ServicelinkerInboundQueue")
                        .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.LINKING).state(JobEvent.State.PENDING).build())
                        .to("direct:updateStatus")
                        .log(LoggingLevel.INFO, correlation() + "Done sending to Servicelinker for enrichment")
                .end()
                .routeId("servicelinker-enrich-after-pre-validation");

        from("google-pubsub:{{servicelinker.pubsub.project.id}}:" + Constants.SERVICELINKER_STATUS_TOPIC)
                .process(e -> e.getIn().setHeader(PROVIDER_ID, getProviderRepository().getProviderId(e.getIn().getHeader(DATASET_REFERENTIAL, String.class))))
                .setHeader(CHOUETTE_REFERENTIAL, header(DATASET_REFERENTIAL))
                .choice()
                .when(header(Constants.LINKING_NETEX_FILE_STATUS_HEADER).isEqualTo(Constants.LINKING_NETEX_FILE_STATUS_SUCCEEDED))
                    .log(LoggingLevel.INFO, correlation() + "Received notification that Servicelinker enrichment has succeeded. File location: ${header." + Constants.LINKED_NETEX_FILE_PATH_HEADER + "}")
                    .to("direct:copyEnrichedDatasetToInternalBucket")
                    .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.LINKING).state(JobEvent.State.OK).build())
                .endChoice()
                .when(header(Constants.LINKING_NETEX_FILE_STATUS_HEADER).isEqualTo(Constants.LINKING_NETEX_FILE_STATUS_STARTED))
                    .log(LoggingLevel.INFO, correlation() + "Received notification that Servicelinker enrichment has started.")
                    .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.LINKING).state(JobEvent.State.STARTED).build())
                    .to("direct:updateStatus")
                    .stop()
                .endChoice()
                .when(header(Constants.LINKING_NETEX_FILE_STATUS_HEADER).isEqualTo(Constants.LINKING_NETEX_FILE_STATUS_FAILED))
                    .log(LoggingLevel.WARN, correlation() + "Received notification that Servicelinker enrichment has failed. Continuing with original file.")
                    .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.LINKING).state(JobEvent.State.FAILED).errorCode(e.getIn().getHeader(LINKING_ERROR_CODE_HEADER, String.class)).build())
                .endChoice()
                .otherwise()
                    .log(LoggingLevel.ERROR, correlation() + "Received notification with unknown Servicelinker linking status: ${header." + Constants.LINKING_NETEX_FILE_STATUS_HEADER + "}")
                    .stop()
                .end() // end otherwise
                .end() // end choice
                .to("direct:updateStatus")
                .to("direct:ashurNetexFilterAfterPreValidation")
                .routeId("servicelinker-enrichment-status-route");

        from("direct:copyEnrichedDatasetToInternalBucket")
                // Store the enriched file at a dedicated path in the internal bucket, leaving the original untouched
                .setHeader(FILE_HANDLE, header(Constants.LINKED_NETEX_FILE_PATH_HEADER))
                .setHeader(TARGET_FILE_HANDLE, header(Constants.LINKED_NETEX_FILE_PATH_HEADER))
                .setHeader(SOURCE_CONTAINER, simple("${properties:blobstore.gcs.servicelinker.exchange.container.name}"))
                .to("direct:copyBlobFromAnotherBucketToInternalBucket")
                .end()
                .routeId("copy-enriched-dataset-to-internal-bucket-route");

    }
}
