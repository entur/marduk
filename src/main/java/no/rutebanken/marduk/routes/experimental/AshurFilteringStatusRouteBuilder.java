package no.rutebanken.marduk.routes.experimental;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;

@Component
public class AshurFilteringStatusRouteBuilder extends BaseRouteBuilder {
    @Override
    public void configure() throws Exception {
        super.configure();

        from("google-pubsub:{{marduk.pubsub.project.id}}:" + Constants.FILTER_NETEX_FILE_STATUS_TOPIC)
                .choice()
                .when(header(Constants.FILTER_NETEX_FILE_STATUS_HEADER).isEqualTo(Constants.FILTER_NETEX_FILE_STATUS_SUCCEEDED))
                    .log(LoggingLevel.INFO, correlation() + " Received notification that Ashur filtering has succeeded. File location: ${header." + Constants.FILTERED_NETEX_FILE_PATH_HEADER + "}")
                    .to("direct:postValidateFilteredDataset")
                .endChoice()
                .when(header(Constants.FILTER_NETEX_FILE_STATUS_HEADER).isEqualTo(Constants.FILTER_NETEX_FILE_STATUS_FAILED))
                    .log(LoggingLevel.INFO, correlation() + " Recieved notification that Ashur filtering has failed.")
                .end();

        from("direct:postValidateFilteredDataset")
                .log(LoggingLevel.INFO, correlation() + " Copying filtered dataset to marduk internal bucket.")
                .to("direct:copyFilteredDatasetToInternalBucket")
                .to("direct:copyInternalBlobToValidationBucket")
                .log(LoggingLevel.INFO, correlation() + " Triggering post-validation of filtered dataset in Antu.")
                .to("direct:triggerAntuPostValidation")
                .end()
                .routeId("antu-post-validation-preparation-route");

        from("direct:copyFilteredDatasetToInternalBucket")
                .setHeader(FILE_HANDLE, header(Constants.FILTERED_NETEX_FILE_PATH_HEADER))
                .setHeader(TARGET_FILE_HANDLE, header(Constants.FILTERED_NETEX_FILE_PATH_HEADER))
                .setHeader(SOURCE_CONTAINER, simple("${properties:blobstore.gcs.ashur.exchange.container.name}"))
                .to("direct:copyBlobFromAnotherBucketToInternalBucket")
                .end()
                .routeId("copy-filtered-dataset-to-internal-bucket-route");

        from("direct:triggerAntuPostValidation")
                .setHeader(VALIDATION_STAGE_HEADER, constant(VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION))
                .setHeader(VALIDATION_CLIENT_HEADER, constant(VALIDATION_CLIENT_MARDUK))
                .setHeader(VALIDATION_PROFILE_HEADER, constant(VALIDATION_PROFILE_TIMETABLE))
                .setHeader(VALIDATION_DATASET_FILE_HANDLE_HEADER, header(FILE_HANDLE))
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
