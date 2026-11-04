package no.rutebanken.marduk.chouette;

import com.fasterxml.jackson.databind.JsonMappingException;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import no.rutebanken.marduk.pipeline.MardukMdc;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pipeline.RetryPolicy;
import no.rutebanken.marduk.pubsub.InFlightWork;
import no.rutebanken.marduk.pubsub.MardukPubSubConsumer;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.routes.chouette.json.ActionReportWrapper;
import no.rutebanken.marduk.routes.chouette.json.JobResponseWithLinks;
import no.rutebanken.marduk.routes.chouette.json.Status;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_ID;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_JOB_TYPE;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_URL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

/**
 * Follows a Chouette job to its end, then hands the result to whoever asked for it.
 *
 * <p>Chouette has no callback, so the job is polled: read its status, and if it has not finished, put the
 * same message back on the queue after a delay. The queue is the state - what has been polled, how often, and
 * where the answer goes are all headers on the message - so a poll that outlives a pod is picked up by
 * another one.
 *
 * <p>Replaces {@code ChouettePollJobStatusRoute}'s consumer and the four routes only it called. The
 * destination header still holds a {@code direct:} route name: the five steps that submit jobs are still
 * Camel routes, and they are what read the result.
 */
@Component
public class ChouetteJobPoller extends MardukPubSubConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChouetteJobPoller.class);

    /** The states that mean "come back later". */
    private static final Set<Status> UNFINISHED = EnumSet.of(Status.SCHEDULED, Status.STARTED, Status.RESCHEDULED);

    private static final String LOOP_COUNTER = "loopCounter";
    private static final String ACTION_REPORT_RESULT = "action_report_result";
    private static final String VALIDATION_REPORT_RESULT = "validation_report_result";
    private static final String DATA_URL = "data_url";

    /**
     * Chouette's own failure code, passed to the result handler so it can turn it into an error code an
     * operator recognises. In-process only: the handler runs from here, so this never goes on a queue.
     */
    static final String CHOUETTE_FAILURE_CODE = "chouette_failure_code";

    private final ChouetteClient chouetteClient;
    private final MardukPubSubPublisher publisher;
    private final JobEventPublisher jobEvents;
    private final Map<String, ChouetteJobResultHandler> resultHandlers;
    private final TaskScheduler taskScheduler;
    private final InFlightWork inFlightWork;
    private final RetryPolicy retryPolicy;
    private final int maxRetries;
    private final Duration retryDelay;

    public ChouetteJobPoller(
            ChouetteClient chouetteClient,
            MardukPubSubPublisher publisher,
            JobEventPublisher jobEvents,
            List<ChouetteJobResultHandler> resultHandlers,
            TaskScheduler taskScheduler,
            InFlightWork inFlightWork,
            RetryPolicy retryPolicy,
            @Value("${chouette.max.retries:3000}") int maxRetries,
            @Value("${chouette.retry.delay:15000}") long retryDelayMillis) {
        this.chouetteClient = chouetteClient;
        this.publisher = publisher;
        this.jobEvents = jobEvents;
        this.resultHandlers = resultHandlers.stream()
                .collect(Collectors.toMap(ChouetteJobResultHandler::destination, handler -> handler));
        this.taskScheduler = taskScheduler;
        this.inFlightWork = inFlightWork;
        this.retryPolicy = retryPolicy;
        this.maxRetries = maxRetries;
        this.retryDelay = Duration.ofMillis(retryDelayMillis);
    }

    @Override
    protected String destination() {
        return MardukQueues.CHOUETTE_POLL_STATUS_QUEUE;
    }

    @Override
    protected void handle(MardukMessage message) {
        require(message, CORRELATION_ID);
        require(message, PROVIDER_ID);
        require(message, CHOUETTE_JOB_STATUS_ROUTING_DESTINATION);
        require(message, CHOUETTE_JOB_STATUS_URL);
        require(message, CHOUETTE_JOB_STATUS_JOB_TYPE);

        int attempt = message.getHeader(LOOP_COUNTER, 0, Integer.class) + 1;
        message.setHeader(LOOP_COUNTER, attempt);
        String jobId = message.getHeader(CHOUETTE_JOB_ID, String.class);
        String statusUrl = message.getHeader(CHOUETTE_JOB_STATUS_URL, String.class);
        LOGGER.debug("Checking status for job {}. Polling counter: {}", jobId, attempt);

        JobResponseWithLinks job = read(chouetteClient.getString(statusUrl), JobResponseWithLinks.class,
                "job status");
        Status status = job.getStatus();
        if (UNFINISHED.contains(status) && attempt <= maxRetries) {
            reschedule(message, status, attempt);
            return;
        }
        finish(message, job, status, attempt);
    }

    /**
     * Puts the message back on the queue after the retry delay.
     *
     * <p>Delayed on the scheduler rather than by holding the message: the route it replaces used
     * {@code delay().asyncDelayed()} for the same reason, so one slow job does not occupy a consumer thread
     * for its whole lifetime. Counted as in-flight work so a graceful shutdown waits for the republish
     * rather than dropping the poll chain. A pod killed without warning inside the delay window still loses
     * the poll: the library acks as soon as {@link #handle} returns and exposes no manual ack, so there is
     * nothing left to redeliver.
     *
     * <p>The republish is the whole poll chain, so it must not be able to fail quietly: the exception a
     * scheduled task throws goes into a {@code Future} nobody reads, which would strand the Chouette job
     * with no log line at all. It is retried and then logged at ERROR, and a task the scheduler refuses -
     * which is what happens once shutdown has begun - is published inline instead, where a failure still
     * reaches the caller and nacks the message.
     */
    private void reschedule(MardukMessage message, Status status, int attempt) {
        if (status == Status.STARTED && attempt == 1) {
            jobEvents.reportProviderJob(message, builder -> builder
                    .timetableAction(jobType(message))
                    .state(JobEvent.State.STARTED)
                    .jobId(message.getHeader(CHOUETTE_JOB_ID, String.class)));
        }
        message.setBody("");
        MardukMessage queued = message.copy();
        String jobId = queued.getHeader(CHOUETTE_JOB_ID, String.class);
        InFlightWork.Tracked tracked = inFlightWork.start();
        try {
            taskScheduler.schedule(
                    () -> {
                        try (tracked) {
                            MardukMdc.with(queued, () -> republish(queued, jobId));
                        }
                    },
                    Instant.now().plus(retryDelay));
        } catch (RuntimeException e) {
            // Nothing will run the task, so the poll goes out now rather than being dropped, and the
            // counter has to come back down here or every later shutdown waits out the whole drain.
            LOGGER.warn("Could not schedule the delayed poll of job {}, publishing it now", jobId, e);
            try (tracked) {
                publisher.publish(MardukQueues.CHOUETTE_POLL_STATUS_QUEUE, queued);
            }
        }
    }

    private void republish(MardukMessage queued, String jobId) {
        try {
            retryPolicy.run("Rescheduling the poll of Chouette job " + jobId,
                    () -> publisher.publish(MardukQueues.CHOUETTE_POLL_STATUS_QUEUE, queued));
        } catch (RuntimeException e) {
            LOGGER.error("Gave up putting the poll of Chouette job {} back on the queue. The job will not "
                    + "be followed any further and its status will stay unreported.", jobId, e);
        }
    }

    private void finish(MardukMessage message, JobResponseWithLinks job, Status status, int attempt) {
        LOGGER.debug("Exited retry loop with status {} for job {}", status,
                message.getHeader(CHOUETTE_JOB_ID, String.class));
        if (UNFINISHED.contains(status)) {
            LOGGER.warn("Job timed out with state {}. Config should probably be tweaked. Stopping route.", status);
            report(message, JobEvent.State.TIMEOUT);
            return;
        }
        if (status == Status.ABORTED) {
            LOGGER.warn("Job ended in state FAILED. Stopping route.");
            report(message, JobEvent.State.FAILED);
            return;
        }
        if (status == Status.CANCELED) {
            LOGGER.warn("Job ended in state CANCELLED. Stopping route.");
            report(message, JobEvent.State.CANCELLED);
            return;
        }

        message.setHeader(DATA_URL, link(job, "data"));
        String actionReportUrl = link(job, "action_report");
        if (actionReportUrl == null) {
            throw new IllegalArgumentException("No URL found for action report.");
        }
        String validationReportUrl = link(job, "validation_report");

        LOGGER.debug("Calling action report url {}", actionReportUrl);
        ActionReportWrapper report;
        try {
            report = read(chouetteClient.getString(actionReportUrl), ActionReportWrapper.class, "action report");
        } catch (MardukException e) {
            if (!(e.getCause() instanceof JsonMappingException)) {
                throw e;
            }
            LOGGER.warn("Received invalid (empty?) action report for terminated job. Giving up.", e);
            report(message, JobEvent.State.FAILED);
            return;
        }

        if (!report.isFinalised()) {
            if (attempt > maxRetries) {
                LOGGER.warn("Received non-finalised action report for terminated job. Giving up.");
                report(message, JobEvent.State.FAILED);
            } else {
                LOGGER.info("Received non-finalised action report for terminated job. Waiting before retry ");
                reschedule(message, status, attempt);
            }
            return;
        }

        ActionReportWrapper.ActionReport actionReport = report.getActionReport();
        message.setHeader(ACTION_REPORT_RESULT, actionReport.getResult());
        ActionReportWrapper.Failure failure = actionReport.getFailure();
        if (failure != null) {
            message.setHeader(CHOUETTE_FAILURE_CODE, failure.getCode());
            if (JobEvent.CHOUETTE_JOB_FAILURE_CODE_NO_DATA_FOUND.equals(failure.getCode())) {
                message.setHeader(Constants.JOB_ERROR_CODE, JobEvent.JOB_ERROR_VALIDATION_NO_DATA);
            }
        }

        if (validationReportUrl == null) {
            message.setHeader(VALIDATION_REPORT_RESULT, ChouetteValidationReport.NOT_PRESENT);
        } else {
            LOGGER.debug("Calling validation report url {}", validationReportUrl);
            message.setHeader(VALIDATION_REPORT_RESULT,
                    ChouetteValidationReport.verdict(chouetteClient.getString(validationReportUrl)));
        }
        LOGGER.debug("action_report_result={} validation_report_result={}",
                message.getHeader(ACTION_REPORT_RESULT), message.getHeader(VALIDATION_REPORT_RESULT));

        dispatch(message);
    }

    /**
     * To the handler the submitting step named.
     *
     * <p>An unknown destination throws rather than being dropped: the value is on the wire, so the only way
     * to see one is a message written by a version that knows a destination this one does not. Nacking lets a
     * pod that does know it pick the message up, which matters during a rolling deploy.
     */
    private void dispatch(MardukMessage message) {
        String destination = message.getHeader(CHOUETTE_JOB_STATUS_ROUTING_DESTINATION, String.class);
        ChouetteJobResultHandler handler = resultHandlers.get(destination);
        if (handler == null) {
            throw new IllegalArgumentException("No handler for Chouette job result destination " + destination);
        }
        handler.handle(message);
    }

    private void report(MardukMessage message, JobEvent.State state) {
        jobEvents.reportProviderJob(message, builder -> builder.timetableAction(jobType(message)).state(state));
    }

    private static JobEvent.TimetableAction jobType(MardukMessage message) {
        return JobEvent.TimetableAction.valueOf(message.getHeader(CHOUETTE_JOB_STATUS_JOB_TYPE, String.class));
    }

    private static String link(JobResponseWithLinks job, String rel) {
        return job.getLinks() == null ? null : job.getLinks().stream()
                .filter(link -> rel.equals(link.getRel()))
                .findFirst()
                .map(JobResponseWithLinks.LinkInfo::getHref)
                .orElse(null);
    }

    private static <T> T read(String json, Class<T> type, String what) {
        try {
            return ObjectMapperFactory.getSharedObjectMapper().readValue(json, type);
        } catch (IOException e) {
            throw new MardukException("Could not read the " + what + " Chouette returned", e);
        }
    }

    /** The route validated these five up front; a message without them can only fail further in. */
    private static void require(MardukMessage message, String header) {
        if (message.getHeader(header) == null) {
            throw new IllegalArgumentException("A poll request without " + header + " cannot be followed");
        }
    }
}
