package no.rutebanken.marduk.chouette;

import jakarta.annotation.PreDestroy;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.pipeline.MardukMdc;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.processors.NightlyValidationFileProcessor;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import no.rutebanken.marduk.validation.AntuValidation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.USERNAME;

/**
 * Starts validation across every provider that has asked for it.
 *
 * <p>Two sweeps, both nightly and both opt-in per provider: antu re-validates the last dataset that passed
 * pre-validation, and Chouette runs a level 2 validation. Which providers each one covers is decided by
 * whether they migrate their data onwards - a provider that does is a level 1 dataspace, and level 2 happens
 * in the dataspace it migrates into.
 *
 * <p>Replaces {@code direct:triggerAntuValidationForAllProviders},
 * {@code direct:chouetteValidateLevel2ForAllProviders} and
 * {@code direct:triggerChouetteValidationLevel1ForProvider}.
 */
@Component
public class ChouetteValidationTriggers {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChouetteValidationTriggers.class);

    /** The routes stamped this on the events, so nabu shows the sweeps as the system's work, not a person's. */
    private static final String SYSTEM = "System";

    private final ProviderRepository providerRepository;
    private final MardukInternalBlobStoreService internalBlobStore;
    private final AntuValidation antuValidation;
    private final MardukPubSubPublisher publisher;

    /**
     * Both sweeps fan out over their providers, bounded at the same 20 as the Camel pool the split used, so
     * a slow dataspace cannot hold up the rest of the night.
     */
    private final ExecutorService providerFanOut = Executors.newFixedThreadPool(
            20, Thread.ofPlatform().name("validation-all-providers-", 0).factory());

    public ChouetteValidationTriggers(
            ProviderRepository providerRepository,
            MardukInternalBlobStoreService internalBlobStore,
            AntuValidation antuValidation,
            MardukPubSubPublisher publisher) {
        this.providerRepository = providerRepository;
        this.internalBlobStore = internalBlobStore;
        this.antuValidation = antuValidation;
        this.publisher = publisher;
    }

    /** Asks antu to re-validate every level 1 provider's last pre-validated dataset. */
    public void triggerAntuValidationForAllProviders() {
        NightlyValidationFileProcessor lastPrevalidatedFile =
                new NightlyValidationFileProcessor(internalBlobStore);
        forEveryProvider(candidates(true), provider -> {
            MardukMessage message = sweepRequest(provider)
                    .setHeader(DATASET_REFERENTIAL, provider.getChouetteInfo().getReferential());
            MardukMdc.with(message, () -> {
                lastPrevalidatedFile.locate(message);
                if (!antuValidation.requestNightlyValidationIfFilePresent(message)) {
                    LOGGER.info("No file found for nightly validation in last-prevalidated-files. "
                            + "Triggering validation level 1 in Chouette instead.");
                    validateLevel1(message);
                }
            });
        });
    }

    /** Runs a Chouette level 2 validation for every provider that keeps its own data. */
    public void validateLevel2ForAllProviders() {
        forEveryProvider(candidates(false), provider -> {
            MardukMessage message = sweepRequest(provider);
            MardukMdc.with(message, () -> validate(message, JobEvent.TimetableAction.VALIDATION_LEVEL_2));
        });
    }

    /** The fallback when antu has no file to re-validate for a provider. */
    private void validateLevel1(MardukMessage message) {
        validate(message.copy(), JobEvent.TimetableAction.VALIDATION_LEVEL_1);
    }

    private void validate(MardukMessage message, JobEvent.TimetableAction level) {
        message.setHeader(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL, level.name());
        message.setBody("");
        publisher.publish(MardukQueues.CHOUETTE_VALIDATION_QUEUE, message);
    }

    /**
     * @param level1 true for the providers that migrate their data onwards, false for the ones that keep it
     */
    private List<Provider> candidates(boolean level1) {
        return providerRepository.getProviders().stream().filter(autoValidation(level1)).toList();
    }

    private static Predicate<Provider> autoValidation(boolean level1) {
        return provider -> {
            ChouetteInfo info = provider.getChouetteInfo();
            return info.isAutoValidationCandidate() && (info.getMigrateDataToProvider() != null) == level1;
        };
    }

    /**
     * Runs {@code work} for every provider and waits for all of them.
     *
     * <p>The first failure is rethrown once every task has finished, so one provider whose dataspace or blob
     * store is unreachable does not stop the sweep - which is what {@code parallelProcessing()} on the split
     * did. It failed the exchange afterwards all the same: a split with no aggregation strategy gets
     * {@code UseOriginalAggregationStrategy(exchange, true)}, which copies a sub-exchange's exception onto the
     * parent. {@code stopOnException} only decided whether the remaining providers still ran.
     *
     * <p>Mirrors {@code ChouetteJobs.forEveryProvider}.
     */
    private void forEveryProvider(List<Provider> providers, Consumer<Provider> work) {
        List<Future<?>> running = new ArrayList<>();
        providers.forEach(provider -> running.add(providerFanOut.submit(() -> work.accept(provider))));
        RuntimeException firstFailure = null;
        for (Future<?> task : running) {
            try {
                task.get();
            } catch (ExecutionException e) {
                LOGGER.warn("A provider failed during a nightly validation sweep", e.getCause());
                if (firstFailure == null) {
                    firstFailure = new MardukException("A nightly validation sweep failed", e.getCause());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MardukException("Interrupted during a nightly validation sweep", e);
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    @PreDestroy
    void stopFanOut() {
        providerFanOut.shutdownNow();
    }

    private static MardukMessage sweepRequest(Provider provider) {
        return new MardukMessage()
                .setHeader(CORRELATION_ID, UUID.randomUUID().toString())
                .setHeader(PROVIDER_ID, provider.getId())
                .setHeader(CHOUETTE_REFERENTIAL, provider.getChouetteInfo().getReferential())
                .setHeader(USERNAME, SYSTEM);
    }
}
