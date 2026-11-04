package no.rutebanken.marduk.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Utils.getLastPathElementOfUrl;

/**
 * Turns the {@code Location} of a submitted Chouette job into a poll request.
 *
 * <p>Every step that submits a job does the same four things with the answer: remember where to ask about it,
 * remember its id, say where the result should go, and put the request on the poll queue. The headers are the
 * contract between the submitting step and {@link ChouetteJobPoller}.
 */
@Component
public class ChouetteJobSubmission {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChouetteJobSubmission.class);

    private final MardukPubSubPublisher publisher;

    public ChouetteJobSubmission(MardukPubSubPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * @param jobLocation      the {@code Location} Chouette answered the submission with
     * @param resultHandler    the {@code direct:} destination the poller hands the finished job to
     * @param jobType          the timetable action the job's status events are reported under
     */
    public void pollUntilDone(MardukMessage message, String jobLocation,
                              String resultHandler, JobEvent.TimetableAction jobType) {
        message.setHeader(Constants.CHOUETTE_JOB_STATUS_URL, jobLocation);
        message.setHeader(Constants.CHOUETTE_JOB_ID, getLastPathElementOfUrl(jobLocation));
        message.setHeader(Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION, resultHandler);
        message.setHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE, jobType.name());
        // A previous job's poll count must not carry over, or the new job starts near its retry cap.
        message.removeHeader("loopCounter");
        message.setBody("");
        LOGGER.debug("Chouette job {} submitted, polling until it finishes",
                message.getHeader(Constants.CHOUETTE_JOB_ID));
        publisher.publish(MardukQueues.CHOUETTE_POLL_STATUS_QUEUE, message);
    }
}
