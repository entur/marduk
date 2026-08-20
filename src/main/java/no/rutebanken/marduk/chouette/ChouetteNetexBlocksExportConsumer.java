package no.rutebanken.marduk.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubConsumer;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.chouette.json.Parameters;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

/**
 * Asks Chouette for a second NeTEx export, this one carrying vehicle blocks.
 *
 * <p>Only for the providers that have asked for it: blocks are extra data most codespaces do not produce, and
 * the export is a full Chouette job.
 *
 * <p>Replaces {@code ChouetteExportNetexBlocksRouteBuilder}'s consumer.
 */
@Component
public class ChouetteNetexBlocksExportConsumer extends MardukPubSubConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChouetteNetexBlocksExportConsumer.class);

    private final ChouetteClient chouetteClient;
    private final ProviderRepository providerRepository;
    private final JobEventPublisher jobEvents;
    private final ChouetteJobSubmission submission;
    private final boolean enablePostValidation;
    private final List<String> allowedCodespacesForStopExport;

    public ChouetteNetexBlocksExportConsumer(
            ChouetteClient chouetteClient,
            ProviderRepository providerRepository,
            JobEventPublisher jobEvents,
            ChouetteJobSubmission submission,
            @Value("${chouette.enablePostValidation:true}") boolean enablePostValidation,
            @Value("${chouette.include.stops.codespaces:}") List<String> allowedCodespacesForStopExport) {
        this.chouetteClient = chouetteClient;
        this.providerRepository = providerRepository;
        this.jobEvents = jobEvents;
        this.submission = submission;
        this.enablePostValidation = enablePostValidation;
        this.allowedCodespacesForStopExport = allowedCodespacesForStopExport;
    }

    @Override
    protected String destination() {
        return MardukQueues.CHOUETTE_EXPORT_NETEX_BLOCKS_QUEUE;
    }

    @Override
    protected void handle(MardukMessage message) {
        ensureCorrelationId(message);
        message.removeHeader(Constants.CHOUETTE_JOB_ID);

        Provider provider = providerRepository.getProvider(message.getHeader(PROVIDER_ID, Long.class));
        if (!provider.getChouetteInfo().isEnableBlocksExport()) {
            LOGGER.info("Skipping Chouette Netex Blocks export");
            return;
        }
        LOGGER.info("Starting Chouette Netex Blocks export");
        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS).state(JobEvent.State.PENDING));

        String referential = provider.getChouetteInfo().getReferential();
        message.setHeader(CHOUETTE_REFERENTIAL, referential);
        String codespace = referential.replace("rb_", "").toUpperCase(Locale.ROOT);

        String jobLocation = chouetteClient.postMultipart(
                "/chouette_iev/referentials/" + referential + "/exporter/netexprofile",
                ChouetteMultipart.parameters(Parameters.getNetexBlocksExportParameters(
                        provider, allowedCodespacesForStopExport.contains(codespace), enablePostValidation)));

        submission.pollUntilDone(message, jobLocation,
                ChouetteNetexBlocksExportResultHandler.DESTINATION,
                JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS);
    }
}
