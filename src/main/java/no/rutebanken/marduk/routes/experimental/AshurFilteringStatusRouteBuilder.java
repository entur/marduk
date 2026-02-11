package no.rutebanken.marduk.routes.experimental;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;

/**
 * If Ashur filtering reports that filtering has succeeded, this route will copy the filtered dataset to Marduk internal bucket
 * and trigger post-validation in Antu.
 * */
@Component
public class AshurFilteringStatusRouteBuilder extends BaseRouteBuilder {

    private final ExperimentalImportHelpers experimentalImportHelpers;

    public AshurFilteringStatusRouteBuilder(ExperimentalImportHelpers experimentalImportHelpers) {
        this.experimentalImportHelpers = experimentalImportHelpers;
    }

    @Override
    public void configure() throws Exception {
        super.configure();

        from("google-pubsub:{{ashur.pubsub.project.id}}:" + Constants.FILTER_NETEX_FILE_STATUS_TOPIC)
                .choice()
                .when(header(Constants.FILTER_NETEX_FILE_STATUS_HEADER).isEqualTo(FILTER_NETEX_FILE_STATUS_STARTED))
                    .log(LoggingLevel.INFO, correlation() + " Received notification that Ashur filtering has started.")
                    .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILTERING).state(JobEvent.State.STARTED).build())
                .endChoice()
                .when(header(Constants.FILTER_NETEX_FILE_STATUS_HEADER).isEqualTo(Constants.FILTER_NETEX_FILE_STATUS_SUCCEEDED))
                    .log(LoggingLevel.INFO, correlation() + " Received notification that Ashur filtering has succeeded. File location: ${header." + Constants.FILTERED_NETEX_FILE_PATH_HEADER + "}")
                    .to("direct:postValidateFilteredDataset")
                    .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILTERING).state(JobEvent.State.OK).build())
                .endChoice()
                .when(header(Constants.FILTER_NETEX_FILE_STATUS_HEADER).isEqualTo(Constants.FILTER_NETEX_FILE_STATUS_FAILED))
                    .log(LoggingLevel.INFO, correlation() + " Received notification that Ashur filtering has failed.")
                    .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILTERING).state(JobEvent.State.FAILED).errorCode(e.getIn().getHeader(FILTERING_ERROR_CODE_HEADER, String.class)).build())
                .endChoice()
                .otherwise()
                    .log(LoggingLevel.ERROR, correlation() + " Received notification with unknown Ashur filtering status: ${header." + Constants.FILTER_NETEX_FILE_STATUS_HEADER + "}")
                    .stop()
                .end() // end otherwise
                .end() // end choice
                .to("direct:updateStatus")
                .routeId("ashur-filtering-status-route");

        from("direct:postValidateFilteredDataset")
                .to("direct:copyFilteredDatasetToInternalBucket")
                .to("direct:copyFilteredDatasetToValidationBucket")
                .log(LoggingLevel.INFO, correlation() + " Triggering post-validation of filtered dataset in Antu.")
                .to("direct:triggerAntuPostValidation")
                .end()
                .routeId("antu-post-validation-preparation-route");

        from("direct:copyFilteredDatasetToInternalBucket")
                .setHeader(FILE_HANDLE, header(Constants.FILTERED_NETEX_FILE_PATH_HEADER))
                .setHeader(TARGET_FILE_HANDLE).method(experimentalImportHelpers, "pathToNetexWithoutBlocksProducedByAshur")
                .setHeader(SOURCE_CONTAINER, simple("${properties:blobstore.gcs.ashur.exchange.container.name}"))
                .to("direct:copyBlobFromAnotherBucketToInternalBucket")
                .end()
                .routeId("copy-filtered-dataset-to-internal-bucket-route");

        from("direct:copyFilteredDatasetToValidationBucket")
                .setHeader(FILE_HANDLE).method(experimentalImportHelpers, "pathToNetexWithoutBlocksProducedByAshur")
                .to("direct:copyInternalBlobToValidationBucket")
                .routeId("copy-filtered-dataset-to-validation-bucket-route");

        from("direct:triggerAntuPostValidation")
                .setHeader(VALIDATION_STAGE_HEADER, constant(VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION))
                .setHeader(VALIDATION_CLIENT_HEADER, constant(VALIDATION_CLIENT_MARDUK))
                .setHeader(VALIDATION_PROFILE_HEADER, constant(VALIDATION_PROFILE_TIMETABLE))
                .setHeader(VALIDATION_DATASET_FILE_HANDLE_HEADER).method(experimentalImportHelpers, "pathToNetexWithoutBlocksProducedByAshur")
                .setHeader(VALIDATION_CORRELATION_ID_HEADER, header(CORRELATION_ID))
                .to("direct:setNetexValidationProfile")
                .to("google-pubsub:{{antu.pubsub.project.id}}:AntuNetexValidationQueue")
                .process(e -> JobEvent.providerJobBuilder(e)
                        .timetableAction(JobEvent.TimetableAction.EXPORT_NETEX_POSTVALIDATION)
                        .state(JobEvent.State.PENDING)
                        .jobId(null)
                        .build())
                .to("direct:updateStatus")
                .routeId("trigger-antu-post-validation-after-filtering-route");

    }
}
