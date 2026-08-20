package no.rutebanken.marduk.routes.experimental;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;

@Component
public class ExportNetexBlocksQueueRouteBuilder extends BaseRouteBuilder {

    private final ExperimentalImportHelpers experimentalImportHelpers;

    public ExportNetexBlocksQueueRouteBuilder(ExperimentalImportHelpers experimentalImportHelpers) {
        this.experimentalImportHelpers = experimentalImportHelpers;
    }

    @Override
    public void configure() throws Exception {
        super.configure();

        from("google-pubsub:{{marduk.pubsub.project.id}}:ExportNetexBlocksQueue")
                .process(e -> e.setProperty("exportBlocks", getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).getChouetteInfo().isEnableBlocksExport()))
                .choice()
                .when(exchangeProperty("exportBlocks").isEqualTo(true))
                    .log(LoggingLevel.INFO, correlation() + "Export blocks flag enabled. Starting export with experimental import")
                    .setHeader(FILE_HANDLE).method(experimentalImportHelpers, "pathToPreFilteringNetexForBlockExport")
                    .setHeader(TARGET_FILE_HANDLE).method(experimentalImportHelpers, "pathToNetexWithBlocksForAshurFiltering")
                    .setHeader(TARGET_CONTAINER, simple("${properties:blobstore.gcs.exchange.container.name}"))
                    .log(LoggingLevel.INFO, correlation() + "Copying file with blocks to exchange bucket for Ashur filtering")
                    .to("direct:copyInternalBlobToAnotherBucket")
                    .log(LoggingLevel.INFO, correlation() + "Triggering Ashur filtering for block export for referential ${header." + DATASET_REFERENTIAL + "}")
                    .setHeader(FILTERING_PROFILE_HEADER, constant(FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS))
                    .setHeader(FILTERING_NETEX_SOURCE_HEADER, constant(FILTERING_NETEX_SOURCE_MARDUK))
                    .to("google-pubsub:{{marduk.pubsub.project.id}}:FilterNetexFileQueue")
                    .log(LoggingLevel.INFO, correlation() + "Done triggering Ashur filtering for block export for referential ${header." + DATASET_REFERENTIAL + "}")
                    .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS).state(JobEvent.State.PENDING).build())
                    .to("direct:updateStatus")
                    .log(LoggingLevel.INFO, correlation() + "Done sending to Ashur for block export.")
                .otherwise()
                    .log(LoggingLevel.INFO, correlation() + "Skipping export of NetEx blocks to Ashur after post-validation because provider has blocks export disabled")
                .end()
                .routeId("export-netex-blocks-queue-route");
    }
}
