package no.rutebanken.marduk.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.repository.InMemoryMardukBlobStoreRepository;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import no.rutebanken.marduk.validation.AntuValidation;
import no.rutebanken.marduk.validation.NetexValidationProfiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CURRENT_PREVALIDATED_NETEX_FILENAME;
import static no.rutebanken.marduk.Constants.USERNAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which providers each nightly sweep covers, and what a provider with nothing to re-validate falls back to.
 */
class ChouetteValidationTriggersTest {

    private static final String ANTU_CONTAINER = "antu-exchange";

    private InMemoryMardukBlobStoreRepository internalRepository;
    private RecordingPubSubPublisher publisher;
    private ChouetteValidationTriggers triggers;

    @BeforeEach
    void setUp() {
        internalRepository = new InMemoryMardukBlobStoreRepository(new ConcurrentHashMap<>());
        internalRepository.setContainerName("marduk-internal");
        publisher = new RecordingPubSubPublisher();

        ProviderRepository providerRepository = mock(ProviderRepository.class);
        when(providerRepository.getProviders()).thenReturn(List.of(
                provider(1L, "rb_migrating", 2L, true),
                provider(2L, "rb_final", null, true),
                provider(3L, "rb_no_auto_validation", null, false),
                provider(4L, "   ", null, true)));
        when(providerRepository.getProvider(1L)).thenReturn(provider(1L, "rb_migrating", 2L, true));
        when(providerRepository.getProvider(2L)).thenReturn(provider(2L, "rb_final", null, true));

        triggers = triggersFor(providerRepository, publisher);
    }

    private ChouetteValidationTriggers triggersFor(
            ProviderRepository providerRepository, RecordingPubSubPublisher publisher) {
        MardukInternalBlobStoreService internalBlobStore =
                new MardukInternalBlobStoreService("marduk-internal", internalRepository);
        AntuValidation antuValidation = new AntuValidation(
                internalBlobStore, providerRepository, new NetexValidationProfiles(List.of(), List.of("OYM")),
                new no.rutebanken.marduk.routes.status.JobEventPublisher(publisher), publisher, ANTU_CONTAINER);
        return new ChouetteValidationTriggers(
                providerRepository, internalBlobStore, antuValidation, publisher);
    }

    private ChouetteValidationTriggers levelTwoSweepOver(
            RecordingPubSubPublisher publisher, String... referentials) {
        ProviderRepository repository = mock(ProviderRepository.class);
        List<Provider> providers = new java.util.ArrayList<>();
        for (int i = 0; i < referentials.length; i++) {
            providers.add(provider(i + 1L, referentials[i], null, true));
        }
        when(repository.getProviders()).thenReturn(providers);
        return triggersFor(repository, publisher);
    }

    private static Provider provider(long id, String referential, Long migrateTo, boolean autoValidation) {
        return new Provider()
                .setId(id)
                .setChouetteInfo(new ChouetteInfo()
                        .setReferential(referential)
                        .setMigrateDataToProvider(migrateTo)
                        .setEnableAutoValidation(autoValidation));
    }

    private void storeLastPrevalidatedFileFor(String referential) {
        internalRepository.uploadBlob(
                BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES + referential + "-"
                        + CURRENT_PREVALIDATED_NETEX_FILENAME,
                new ByteArrayInputStream("zip".getBytes(StandardCharsets.UTF_8)));
    }

    private Set<String> referentialsOn(String queue) {
        return publisher.publishedTo(queue).stream()
                .map(p -> p.attributes().get(CHOUETTE_REFERENTIAL))
                .collect(Collectors.toSet());
    }

    @Test
    void antuRevalidationTakesOnlyTheProvidersThatMigrateTheirDataOnwards() {
        // A provider without auto validation, and one whose referential is blank, are not candidates.
        storeLastPrevalidatedFileFor("rb_migrating");
        storeLastPrevalidatedFileFor("rb_final");

        triggers.triggerAntuValidationForAllProviders();

        assertEquals(Set.of("rb_migrating"), referentialsOn(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE));
    }

    @Test
    void chouetteLevelTwoTakesOnlyTheProvidersThatKeepTheirData() {
        triggers.validateLevel2ForAllProviders();

        assertEquals(Set.of("rb_final"), referentialsOn(MardukQueues.CHOUETTE_VALIDATION_QUEUE));
        assertEquals("VALIDATION_LEVEL_2", publisher.publishedTo(MardukQueues.CHOUETTE_VALIDATION_QUEUE)
                .getFirst().attributes().get(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL));
    }

    @Test
    void aProviderWithNothingToRevalidateFallsBackToAChouetteLevelOneValidation() {
        // Nothing stored: antu has no file, so Chouette validates what it already holds.
        triggers.triggerAntuValidationForAllProviders();

        assertTrue(publisher.publishedTo(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE).isEmpty());
        var validation = publisher.publishedTo(MardukQueues.CHOUETTE_VALIDATION_QUEUE);
        assertEquals(Set.of("rb_migrating"), referentialsOn(MardukQueues.CHOUETTE_VALIDATION_QUEUE));
        assertEquals("VALIDATION_LEVEL_1",
                validation.getFirst().attributes().get(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL));
    }

    @Test
    void theSweepsAreRecordedAsTheSystemsWork() {
        // nabu shows a username per job; a nightly sweep has no person behind it.
        triggers.validateLevel2ForAllProviders();

        assertEquals("System", publisher.publishedTo(MardukQueues.CHOUETTE_VALIDATION_QUEUE)
                .getFirst().attributes().get(USERNAME));
    }

    @Test
    void eachProviderInASweepGetsItsOwnCorrelationId() {
        // One id for the whole sweep would merge every provider's job into one in nabu.
        storeLastPrevalidatedFileFor("rb_migrating");

        triggers.triggerAntuValidationForAllProviders();
        triggers.validateLevel2ForAllProviders();

        Set<String> correlationIds = publisher.published().stream()
                .map(p -> p.attributes().get(Constants.CORRELATION_ID))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        assertEquals(2, correlationIds.size(), "the two providers shared a correlation id");
    }

    @Test
    void aPendingPrevalidationIsReportedForTheProviderAntuWillValidate() {
        storeLastPrevalidatedFileFor("rb_migrating");

        triggers.triggerAntuValidationForAllProviders();

        JobEvent reported = JobEvent.fromString(
                publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).getFirst().body());
        assertEquals(JobEvent.TimetableAction.PREVALIDATION.name(), reported.getAction());
        assertEquals(JobEvent.State.PENDING, reported.getState());
    }

    @Test
    void everyProviderIsAttemptedAndTheFailureStillReachesTheCaller() {
        // The split ran every provider because stopOnException was off, and then failed the exchange anyway:
        // its default aggregation strategy copies a sub-exchange's exception onto the parent.
        FailingPublisher publisher = new FailingPublisher("rb_broken");
        ChouetteValidationTriggers sweep = levelTwoSweepOver(publisher, "rb_broken", "rb_healthy", "rb_also_fine");

        assertThrows(MardukException.class, sweep::validateLevel2ForAllProviders);

        assertEquals(Set.of("rb_healthy", "rb_also_fine"), publisher.publishedTo(
                        MardukQueues.CHOUETTE_VALIDATION_QUEUE).stream()
                .map(p -> p.attributes().get(CHOUETTE_REFERENTIAL))
                .collect(Collectors.toSet()));
    }

    @Test
    void theSweepFansOutInParallel() {
        // Both providers have to be in flight at once for the barrier to trip; a sequential sweep leaves the
        // first one waiting for a party that never arrives.
        BarrierPublisher publisher = new BarrierPublisher(2);
        ChouetteValidationTriggers sweep = levelTwoSweepOver(publisher, "rb_one", "rb_two");

        sweep.validateLevel2ForAllProviders();

        assertEquals(2, publisher.publishedTo(MardukQueues.CHOUETTE_VALIDATION_QUEUE).size());
    }

    /** Fails one referential, so a test can check the others still got their validation. */
    private static class FailingPublisher extends RecordingPubSubPublisher {

        private final String failing;

        FailingPublisher(String failing) {
            this.failing = failing;
        }

        @Override
        public synchronized void publish(String destination, MardukMessage message) {
            if (failing.equals(message.getHeader(CHOUETTE_REFERENTIAL, String.class))) {
                throw new MardukException("Chouette is down for " + failing);
            }
            super.publish(destination, message);
        }
    }

    /** Lets no provider past the first publish until the given number of them are there. */
    private static class BarrierPublisher extends RecordingPubSubPublisher {

        private final CyclicBarrier allInFlight;

        BarrierPublisher(int parties) {
            this.allInFlight = new CyclicBarrier(parties);
        }

        @Override
        public void publish(String destination, MardukMessage message) {
            try {
                allInFlight.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MardukException("Interrupted while waiting for the other providers", e);
            } catch (BrokenBarrierException | TimeoutException e) {
                throw new MardukException("The sweep did not run the providers in parallel", e);
            }
            synchronized (this) {
                super.publish(destination, message);
            }
        }
    }
}
