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
 * Asks Chouette to export a dataspace as NeTEx.
 *
 * <p>Replaces {@code ChouetteExportNetexRouteBuilder}'s consumer.
 */
@Component
public class ChouetteNetexExportConsumer extends MardukPubSubConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChouetteNetexExportConsumer.class);

    private final ChouetteClient chouetteClient;
    private final ProviderRepository providerRepository;
    private final JobEventPublisher jobEvents;
    private final ChouetteJobSubmission submission;
    private final boolean enablePostValidation;
    private final List<String> allowedCodespacesForStopExport;

    public ChouetteNetexExportConsumer(
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
        return MardukQueues.CHOUETTE_EXPORT_NETEX_QUEUE;
    }

    @Override
    protected void handle(MardukMessage message) {
        ensureCorrelationId(message);
        message.removeHeader(Constants.CHOUETTE_JOB_ID);
        LOGGER.info("Starting Chouette Netex export");
        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(JobEvent.TimetableAction.EXPORT_NETEX).state(JobEvent.State.PENDING));

        Provider provider = providerRepository.getProvider(message.getHeader(PROVIDER_ID, Long.class));
        String referential = provider.getChouetteInfo().getReferential();
        message.setHeader(CHOUETTE_REFERENTIAL, referential);
        String codespace = referential.replace("rb_", "").toUpperCase(Locale.ROOT);

        String jobLocation = chouetteClient.postMultipart(
                "/chouette_iev/referentials/" + referential + "/exporter/netexprofile",
                ChouetteMultipart.parameters(Parameters.getDefaultNetexExportParameters(
                        provider, allowedCodespacesForStopExport.contains(codespace), enablePostValidation)));

        submission.pollUntilDone(message, jobLocation,
                ChouetteNetexExportResultHandler.DESTINATION, JobEvent.TimetableAction.EXPORT_NETEX);
    }
}
