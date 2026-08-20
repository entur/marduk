package no.rutebanken.marduk.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pipeline.RetryPolicy;
import no.rutebanken.marduk.pubsub.MardukPubSubConsumer;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.chouette.json.Parameters;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.FILE_TYPE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

/**
 * Hands a stored dataset to Chouette for import.
 *
 * <p>Replaces {@code ChouetteImportRouteBuilder}'s consumer and the two routes only it called. The result is
 * not waited for here: the job is submitted and {@link ChouetteJobPoller} follows it, which is what makes an
 * import survive a restart of this pod.
 */
@Component
public class ChouetteImportConsumer extends MardukPubSubConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChouetteImportConsumer.class);

    private final ChouetteClient chouetteClient;
    private final MardukInternalBlobStoreService internalBlobStore;
    private final ProviderRepository providerRepository;
    private final JobEventPublisher jobEvents;
    private final ChouetteJobSubmission submission;
    private final RetryPolicy retryPolicy;
    private final boolean enablePreValidation;
    private final List<String> allowedCodespacesForStopUpdate;

    public ChouetteImportConsumer(
            ChouetteClient chouetteClient,
            MardukInternalBlobStoreService internalBlobStore,
            ProviderRepository providerRepository,
            JobEventPublisher jobEvents,
            ChouetteJobSubmission submission,
            RetryPolicy retryPolicy,
            @Value("${chouette.enablePreValidation:true}") boolean enablePreValidation,
            @Value("${chouette.include.stops.codespaces:}") List<String> allowedCodespacesForStopUpdate) {
        this.chouetteClient = chouetteClient;
        this.internalBlobStore = internalBlobStore;
        this.providerRepository = providerRepository;
        this.jobEvents = jobEvents;
        this.submission = submission;
        this.retryPolicy = retryPolicy;
        this.enablePreValidation = enablePreValidation;
        this.allowedCodespacesForStopUpdate = allowedCodespacesForStopUpdate;
    }

    @Override
    protected String destination() {
        return MardukQueues.CHOUETTE_IMPORT_QUEUE;
    }

    @Override
    protected void handle(MardukMessage message) {
        LOGGER.info("Starting Chouette import");
        // A retried import must not look like the previous attempt's job.
        message.removeHeader(Constants.CHOUETTE_JOB_ID);
        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(JobEvent.TimetableAction.IMPORT).state(JobEvent.State.PENDING));

        String fileHandle = message.getHeader(FILE_HANDLE, String.class);
        try (InputStream dataset = retryPolicy.call("Reading " + fileHandle,
                () -> internalBlobStore.getBlob(fileHandle))) {
            if (dataset == null) {
                LOGGER.warn("Import failed because blob could not be found");
                jobEvents.reportProviderJob(message, builder -> builder
                        .timetableAction(JobEvent.TimetableAction.IMPORT).state(JobEvent.State.FAILED));
                return;
            }
            submit(message, dataset);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Could not read " + fileHandle + " for import", e);
        }
    }

    private void submit(MardukMessage message, InputStream dataset) {
        Long providerId = message.getHeader(PROVIDER_ID, Long.class);
        Provider provider = providerRepository.getProvider(providerId);
        String referential = provider.getChouetteInfo().getReferential();
        message.setHeader(CHOUETTE_REFERENTIAL, referential);

        String fileType = message.getHeader(FILE_TYPE, String.class);
        String codespace = referential.toUpperCase(Locale.ROOT);
        // GTFS is always pre-validated by Chouette, because antu does not validate it.
        boolean preValidate = FileType.GTFS.name().equals(fileType) || enablePreValidation;
        String parameters = Parameters.createImportParameters(
                message.getHeader(FILE_NAME, String.class), fileType, provider, preValidate,
                allowedCodespacesForStopUpdate.contains(codespace));
        LOGGER.debug("import parameters: {}", parameters);

        String jobLocation = chouetteClient.postMultipart(
                "/chouette_iev/referentials/" + referential + "/importer/" + fileType.toLowerCase(Locale.ROOT),
                ChouetteMultipart.parametersAndFeed(parameters, message.getHeader(FILE_NAME, String.class), dataset));

        submission.pollUntilDone(message, jobLocation,
                ChouetteImportResultHandler.DESTINATION, JobEvent.TimetableAction.IMPORT);
    }
}
