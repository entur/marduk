package no.rutebanken.marduk.routes.experimental;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.routes.experimental.AshurFilteringReportValidator.FILTERING_REPORT_ERROR_HEADER;
import static no.rutebanken.marduk.routes.experimental.AshurFilteringReportValidator.FILTERING_REPORT_VALID_HEADER;

/**
 * If Ashur filtering reports that filtering has succeeded, this route will copy the filtered dataset to Marduk internal bucket
 * and trigger post-validation in Antu.
 * */
@Component
public class AshurFilteringStatusRouteBuilder extends BaseRouteBuilder {

    private final ExperimentalImportHelpers experimentalImportHelpers;
    private final AshurFilteringReportValidator ashurFilteringReportValidator;

    public AshurFilteringStatusRouteBuilder(ExperimentalImportHelpers experimentalImportHelpers, AshurFilteringReportValidator ashurFilteringReportValidator) {
        this.experimentalImportHelpers = experimentalImportHelpers;
        this.ashurFilteringReportValidator = ashurFilteringReportValidator;
    }

    @Override
    public void configure() throws Exception {
        super.configure();

        from("google-pubsub:{{ashur.pubsub.project.id}}:" + Constants.FILTER_NETEX_FILE_STATUS_TOPIC)
                .choice()
                .when(header(Constants.FILTERING_PROFILE_HEADER).isEqualTo(FILTERING_PROFILE_STANDARD_IMPORT))
                    .to("direct:handleStandardImportFilteringStatus")
                .when(header(Constants.FILTERING_PROFILE_HEADER).isEqualTo(FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS))
                    .to("direct:handleIncludeBlocksFilteringStatus")
                .otherwise()
                    .log(LoggingLevel.ERROR, correlation() + " Received notification with unknown Ashur filtering profile: ${header." + FILTERING_PROFILE_HEADER + "}")
                    .stop()
                .end() // end otherwise
                .routeId("ashur-filtering-status-route");

        from("direct:handleStandardImportFilteringStatus")
                .choice()
                .when(header(Constants.FILTER_NETEX_FILE_STATUS_HEADER).isEqualTo(FILTER_NETEX_FILE_STATUS_STARTED))
                    .log(LoggingLevel.INFO, correlation() + " Received notification that Ashur filtering has started for standard import.")
                    .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILTERING).state(JobEvent.State.STARTED).build())
                .endChoice()
                .when(header(Constants.FILTER_NETEX_FILE_STATUS_HEADER).isEqualTo(Constants.FILTER_NETEX_FILE_STATUS_SUCCEEDED))
                    .log(LoggingLevel.INFO, correlation() + " Received notification that Ashur filtering has succeeded for standard import. File location: ${header." + Constants.FILTERED_NETEX_FILE_PATH_HEADER + "}")
                    // Verify filtering report has correct profile and no blocks
                    .process(ashurFilteringReportValidator::validateStandardImportReport)
                    .choice()
                        .when(header(FILTERING_REPORT_VALID_HEADER).isEqualTo(false))
                            .log(LoggingLevel.ERROR, correlation() + "SECURITY: Ashur filtering report validation failed for standard import: ${header." + FILTERING_REPORT_ERROR_HEADER + "}")
                            .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILTERING).state(JobEvent.State.FAILED).errorCode(SECURITY_ERROR_CODE_FILTERING_PROFILE_MISMATCH).build())
                            .to("direct:updateStatus")
                            .stop()
                    .end()
                    .to("direct:postValidateFilteredDataset")
                    .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILTERING).state(JobEvent.State.OK).build())
                .endChoice()
                .when(header(Constants.FILTER_NETEX_FILE_STATUS_HEADER).isEqualTo(Constants.FILTER_NETEX_FILE_STATUS_FAILED))
                    .log(LoggingLevel.INFO, correlation() + " Received notification that Ashur filtering has failed for standard import.")
                    .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILTERING).state(JobEvent.State.FAILED).errorCode(e.getIn().getHeader(FILTERING_ERROR_CODE_HEADER, String.class)).build())
                .endChoice()
                .otherwise()
                    .log(LoggingLevel.ERROR, correlation() + " Received notification with unknown Ashur filtering status for standard import: ${header." + Constants.FILTER_NETEX_FILE_STATUS_HEADER + "}")
                    .stop()
                .end() // end otherwise
                .end() // end choice
                .to("direct:updateStatus")
                .routeId("ashur-filtering-status-standard-import-route");

        from("direct:handleIncludeBlocksFilteringStatus")
                .choice()
                .when(header(Constants.FILTER_NETEX_FILE_STATUS_HEADER).isEqualTo(FILTER_NETEX_FILE_STATUS_STARTED))
                    .log(LoggingLevel.INFO, correlation() + " Received notification that Ashur filtering has started for block export.")
                    .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS).state(JobEvent.State.STARTED).build())
                .endChoice()
                .when(header(Constants.FILTER_NETEX_FILE_STATUS_HEADER).isEqualTo(Constants.FILTER_NETEX_FILE_STATUS_SUCCEEDED))
                    .log(LoggingLevel.INFO, correlation() + " Received notification that Ashur filtering has succeeded for block export. File location: ${header." + Constants.FILTERED_NETEX_FILE_PATH_HEADER + "}")
                    // Verify filtering report has correct profile for blocks export
                    .process(ashurFilteringReportValidator::validateBlocksExportReport)
                    .choice()
                        .when(header(FILTERING_REPORT_VALID_HEADER).isEqualTo(false))
                            .log(LoggingLevel.ERROR, correlation() + "SECURITY: Ashur filtering report validation failed for blocks export: ${header." + FILTERING_REPORT_ERROR_HEADER + "}")
                            .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS).state(JobEvent.State.FAILED).errorCode(SECURITY_ERROR_CODE_FILTERING_PROFILE_MISMATCH).build())
                            .to("direct:updateStatus")
                            .stop()
                    .end()
                    .to("direct:postValidateFilteredDatasetWithBlocks")
                    .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS).state(JobEvent.State.OK).build())
                .endChoice()
                .when(header(Constants.FILTER_NETEX_FILE_STATUS_HEADER).isEqualTo(Constants.FILTER_NETEX_FILE_STATUS_FAILED))
                    .log(LoggingLevel.INFO, correlation() + " Received notification that Ashur filtering has failed for block export.")
                    .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS).state(JobEvent.State.FAILED).build())
                .endChoice()
                .otherwise()
                    .log(LoggingLevel.ERROR, correlation() + " Received notification with unknown Ashur filtering status for block export: ${header." + Constants.FILTER_NETEX_FILE_STATUS_HEADER + "}")
                    .stop()
                .end() // end otherwise
                .end() // end choice
                .to("direct:updateStatus")
                .routeId("ashur-filtering-status-block-export-route");

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

        from("direct:postValidateFilteredDatasetWithBlocks")
                .setHeader(FILE_HANDLE, header(Constants.FILTERED_NETEX_FILE_PATH_HEADER))
                .setHeader(TARGET_FILE_HANDLE).method(experimentalImportHelpers, "pathToNetexWithBlocksProducedByAshur")
                .setHeader(SOURCE_CONTAINER, simple("${properties:blobstore.gcs.ashur.exchange.container.name}"))
                .to("direct:copyBlobFromAnotherBucketToInternalBucket")
                .setHeader(FILE_HANDLE).method(experimentalImportHelpers, "pathToNetexWithBlocksProducedByAshur")
                .to("direct:copyInternalBlobToValidationBucket")
                .to("direct:triggerAntuPostBlocksValidation")
                .routeId("post-validate-filtered-dataset-with-blocks-route");

        from("direct:triggerAntuPostBlocksValidation")
                .setHeader(VALIDATION_STAGE_HEADER, constant(VALIDATION_STAGE_EXPORT_NETEX_BLOCKS_POSTVALIDATION))
                .setHeader(VALIDATION_CLIENT_HEADER, constant(VALIDATION_CLIENT_MARDUK))
                .setHeader(VALIDATION_PROFILE_HEADER, constant(VALIDATION_PROFILE_TIMETABLE))
                .setHeader(VALIDATION_DATASET_FILE_HANDLE_HEADER).method(experimentalImportHelpers, "pathToNetexWithBlocksProducedByAshur")
                .setHeader(VALIDATION_CORRELATION_ID_HEADER, header(CORRELATION_ID))
                .to("direct:setNetexValidationProfile")
                .to("google-pubsub:{{antu.pubsub.project.id}}:AntuNetexValidationQueue")
                .process(e -> JobEvent.providerJobBuilder(e)
                        .timetableAction(JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS_POSTVALIDATION)
                        .state(JobEvent.State.PENDING)
                        .jobId(null)
                        .build())
                .to("direct:updateStatus")
                .routeId("trigger-antu-post-blocks-validation-after-filtering-route");

    }
}
