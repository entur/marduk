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
import org.springframework.stereotype.Component;


import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

/**
 * Asks Chouette to copy a dataspace's data into the dataspace it migrates into.
 *
 * <p>Replaces {@code ChouetteTransferToDataspaceRouteBuilder}'s consumer.
 */
@Component
public class ChouetteTransferConsumer extends MardukPubSubConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChouetteTransferConsumer.class);

    private final ChouetteClient chouetteClient;
    private final ProviderRepository providerRepository;
    private final JobEventPublisher jobEvents;
    private final ChouetteJobSubmission submission;

    public ChouetteTransferConsumer(
            ChouetteClient chouetteClient,
            ProviderRepository providerRepository,
            JobEventPublisher jobEvents,
            ChouetteJobSubmission submission) {
        this.chouetteClient = chouetteClient;
        this.providerRepository = providerRepository;
        this.jobEvents = jobEvents;
        this.submission = submission;
    }

    @Override
    protected String destination() {
        return MardukQueues.CHOUETTE_TRANSFER_EXPORT_QUEUE;
    }

    @Override
    protected void handle(MardukMessage message) {
        ensureCorrelationId(message);
        message.removeHeader(Constants.CHOUETTE_JOB_ID);
        LOGGER.info("Starting Chouette transfer");

        Provider provider = providerRepository.getProvider(message.getHeader(PROVIDER_ID, Long.class));
        Provider destination = providerRepository.getProvider(provider.getChouetteInfo().getMigrateDataToProvider());
        String referential = provider.getChouetteInfo().getReferential();
        // The name Chouette gives the transfer archive; nothing reads the file, but the header is on the
        // message the poller carries and the status events name it.
        message.setHeader(Constants.FILE_HANDLE, "transfer.zip");
        message.setHeader(CHOUETTE_REFERENTIAL, referential);

        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(JobEvent.TimetableAction.DATASPACE_TRANSFER).state(JobEvent.State.PENDING));

        String jobLocation = chouetteClient.postMultipart(
                "/chouette_iev/referentials/" + referential + "/exporter/transfer",
                ChouetteMultipart.parameters(Parameters.getTransferExportParameters(provider, destination)));

        LOGGER.info("Sending transfer export to poll job status");
        submission.pollUntilDone(message, jobLocation,
                ChouetteTransferResultHandler.DESTINATION, JobEvent.TimetableAction.DATASPACE_TRANSFER);
    }
}
