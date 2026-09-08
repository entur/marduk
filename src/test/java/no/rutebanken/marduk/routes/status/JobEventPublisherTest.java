package no.rutebanken.marduk.routes.status;

import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static no.rutebanken.marduk.Constants.ANTU_VALIDATION_REPORT_ID;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_ID;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.JOB_ERROR_CODE;
import static no.rutebanken.marduk.Constants.ORIGINAL_PROVIDER_ID;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.SYSTEM_STATUS;
import static no.rutebanken.marduk.Constants.USERNAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JobEventPublisherTest {

    private RecordingPubSubPublisher publisher;
    private JobEventPublisher jobEvents;

    @BeforeEach
    void setUp() {
        publisher = new RecordingPubSubPublisher();
        jobEvents = new JobEventPublisher(publisher);
    }

    private static MardukMessage providerMessage() {
        return new MardukMessage()
                .setHeader(PROVIDER_ID, 2L)
                .setHeader(CORRELATION_ID, "corr")
                .setHeader(CHOUETTE_REFERENTIAL, "rb_tst")
                .setHeader(FILE_NAME, "netex.zip")
                .setHeader(USERNAME, "user");
    }

    @Test
    void aProviderJobGoesToNabuSQueue() {
        jobEvents.reportProviderJob(providerMessage(),
                b -> b.timetableAction(JobEvent.TimetableAction.IMPORT).state(JobEvent.State.OK));

        assertEquals("JobEventQueue", publisher.onlyPublished().destination());
    }

    @Test
    void theEventIsTheMessageBody() {
        jobEvents.reportProviderJob(providerMessage(),
                b -> b.timetableAction(JobEvent.TimetableAction.IMPORT).state(JobEvent.State.OK));

        JobEvent published = JobEvent.fromString(publisher.onlyPublished().body());

        assertEquals(2L, published.getProviderId());
        assertEquals("corr", published.getCorrelationId());
        assertEquals("rb_tst", published.getReferential());
        assertEquals("netex.zip", published.getName());
        assertEquals("user", published.getUsername());
        assertEquals("IMPORT", published.getAction());
        assertEquals(JobEvent.State.OK, published.getState());
        assertEquals(JobEvent.JobDomain.TIMETABLE, published.getDomain());
    }

    @Test
    void everyPublishableHeaderTravelsAsAnAttribute() {
        jobEvents.reportProviderJob(providerMessage(),
                b -> b.timetableAction(JobEvent.TimetableAction.IMPORT).state(JobEvent.State.OK));

        assertEquals("2", publisher.onlyPublished().attributes().get(PROVIDER_ID));
        assertEquals("corr", publisher.onlyPublished().attributes().get(CORRELATION_ID));
    }

    @Test
    void theSystemStatusHeaderIsSetSoTheNextReportCanContinueTheJob() {
        // The mechanism systemJobBuilder relies on: the previous report is read back off the message.
        MardukMessage message = providerMessage();

        jobEvents.reportProviderJob(message,
                b -> b.timetableAction(JobEvent.TimetableAction.OTP2_BUILD_GRAPH).state(JobEvent.State.STARTED));

        JobEvent recorded = JobEvent.fromString(message.getHeader(SYSTEM_STATUS, String.class));
        assertEquals("OTP2_BUILD_GRAPH", recorded.getAction());
        assertEquals(JobEvent.State.STARTED, recorded.getState());
    }

    @Test
    void aSystemJobContinuesTheJobDescribedByTheSystemStatusHeader() {
        // How the OTP2 graph build reports: a system job with no provider, where the second report carries
        // the correlation id and identity of the first. The domain is always set explicitly by the caller,
        // because a TIMETABLE-domain event without a provider id fails validation.
        MardukMessage message = new MardukMessage();
        jobEvents.reportSystemJob(message, b -> b
                .jobDomain(JobEvent.JobDomain.GRAPH)
                .action(JobEvent.TimetableAction.OTP2_BUILD_GRAPH)
                .state(JobEvent.State.STARTED)
                .correlationId("graph-run-1")
                .fileName("Graph-otp2.obj"));
        publisher.clear();

        jobEvents.reportSystemJob(message, b -> b.jobDomain(JobEvent.JobDomain.GRAPH).state(JobEvent.State.OK));

        JobEvent published = JobEvent.fromString(publisher.onlyPublished().body());
        assertEquals("OTP2_BUILD_GRAPH", published.getAction(), "the action was not carried over");
        assertEquals("graph-run-1", published.getCorrelationId(), "the correlation id was not carried over");
        assertEquals("Graph-otp2.obj", published.getName());
        assertEquals(JobEvent.State.OK, published.getState());
    }

    @Test
    void aSystemJobWithNoPreviousStatusStillNeedsItsOwnIdentity() {
        // initSystemJob contributes nothing when the header is absent, so the caller has to supply enough to
        // pass validation. Pinned because it is the difference between a first and a subsequent report.
        assertThrows(IllegalArgumentException.class, () -> jobEvents.reportSystemJob(new MardukMessage(),
                b -> b.jobDomain(JobEvent.JobDomain.GRAPH).state(JobEvent.State.OK)));
    }

    @Test
    void theOriginalProviderIdWinsWhenTheProviderHasChanged() {
        // A dataspace transfer switches PROVIDER_ID to the destination provider, and the status must stay
        // against the provider that started the chain.
        MardukMessage message = providerMessage()
                .setHeader(PROVIDER_ID, 1002L)
                .setHeader(ORIGINAL_PROVIDER_ID, 2L);

        jobEvents.reportProviderJob(message,
                b -> b.timetableAction(JobEvent.TimetableAction.DATASPACE_TRANSFER).state(JobEvent.State.OK));

        assertEquals(2L, JobEvent.fromString(publisher.onlyPublished().body()).getProviderId());
    }

    @Test
    void theDatasetReferentialIsUsedWhenThereIsNoChouetteReferential() {
        MardukMessage message = new MardukMessage()
                .setHeader(PROVIDER_ID, 2L)
                .setHeader(CORRELATION_ID, "corr")
                .setHeader(DATASET_REFERENTIAL, "TST");

        jobEvents.reportProviderJob(message,
                b -> b.timetableAction(JobEvent.TimetableAction.PREVALIDATION).state(JobEvent.State.PENDING));

        assertEquals("TST", JobEvent.fromString(publisher.onlyPublished().body()).getReferential());
    }

    @Test
    void theAntuReportIdIsUsedAsExternalIdWhenThereIsNoChouetteJobId() {
        MardukMessage message = providerMessage().setHeader(ANTU_VALIDATION_REPORT_ID, "report-1");

        jobEvents.reportProviderJob(message,
                b -> b.timetableAction(JobEvent.TimetableAction.PREVALIDATION).state(JobEvent.State.OK));

        assertEquals("report-1", JobEvent.fromString(publisher.onlyPublished().body()).getExternalId());
    }

    @Test
    void theChouetteJobIdWinsOverTheAntuReportId() {
        MardukMessage message = providerMessage()
                .setHeader(CHOUETTE_JOB_ID, "job-7")
                .setHeader(ANTU_VALIDATION_REPORT_ID, "report-1");

        jobEvents.reportProviderJob(message,
                b -> b.timetableAction(JobEvent.TimetableAction.IMPORT).state(JobEvent.State.OK));

        assertEquals("job-7", JobEvent.fromString(publisher.onlyPublished().body()).getExternalId());
    }

    @Test
    void anErrorCodeOnTheMessageReachesNabu() {
        MardukMessage message = providerMessage().setHeader(JOB_ERROR_CODE, JobEvent.JOB_ERROR_DUPLICATE_FILE);

        jobEvents.reportProviderJob(message,
                b -> b.timetableAction(JobEvent.TimetableAction.FILE_TRANSFER).state(JobEvent.State.FAILED));

        assertEquals("ERROR_FILE_DUPLICATE", JobEvent.fromString(publisher.onlyPublished().body()).getErrorCode());
    }

    @Test
    void anEventWithNoProviderIdFailsRatherThanReportingAgainstTheWrongProvider() {
        MardukMessage message = new MardukMessage().setHeader(CORRELATION_ID, "corr");

        assertThrows(IllegalStateException.class, () -> jobEvents.reportProviderJob(message,
                b -> b.timetableAction(JobEvent.TimetableAction.IMPORT).state(JobEvent.State.OK)));
        assertEquals(0, publisher.published().size(), "a status was published for an unknown provider");
    }

    @Test
    void anEventWithNoStateFailsRatherThanPublishingAPartialStatus() {
        assertThrows(IllegalArgumentException.class, () -> jobEvents.reportProviderJob(providerMessage(),
                b -> b.timetableAction(JobEvent.TimetableAction.IMPORT)));
        assertEquals(0, publisher.published().size());
    }

    @Test
    void nothingIsPublishedBeforeTheEventIsValid() {
        // The order matters: build() throws before publish() is reached, so an invalid event cannot leave a
        // half-built status on the queue.
        MardukMessage message = providerMessage();

        assertThrows(IllegalArgumentException.class,
                () -> jobEvents.reportProviderJob(message, b -> b.state(JobEvent.State.OK)));

        assertNull(message.getHeader(SYSTEM_STATUS));
    }
}
