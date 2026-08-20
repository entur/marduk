package no.rutebanken.marduk.routes.status;

import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * Reports job status to nabu, replacing {@code direct:updateStatus}.
 *
 * <p>The two methods build the event onto the message and publish it in one call, rather than exposing a
 * builder the caller has to remember to publish afterwards. Under Camel those were two steps - a
 * {@code .process()} that built the event and a {@code .to("direct:updateStatus")} - and a route that did
 * the first without the second silently reported nothing.
 *
 * <p>Nabu receives the event as the message body, with every publishable header as attributes.
 */
@Component
public class JobEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobEventPublisher.class);

    private final MardukPubSubPublisher publisher;

    public JobEventPublisher(MardukPubSubPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Reports status for a provider's job. The provider id, referential, correlation id, external job id,
     * user and error code are read off the message.
     *
     * @param configure sets at least the action and the state
     */
    public void reportProviderJob(MardukMessage message, Consumer<JobEvent.Builder> configure) {
        report(JobEvent.providerJobBuilder(message), message, configure);
    }

    /**
     * Reports status for a job with no provider, continuing whatever job the message's
     * {@code RutebankenSystemStatus} header describes.
     */
    public void reportSystemJob(MardukMessage message, Consumer<JobEvent.Builder> configure) {
        report(JobEvent.systemJobBuilder(message), message, configure);
    }

    private void report(JobEvent.Builder builder, MardukMessage message, Consumer<JobEvent.Builder> configure) {
        configure.accept(builder);
        // build() writes the event onto the message as the body and the system status header, which is what
        // the next status report in the same job reads to continue it.
        JobEvent event = builder.build();
        LOGGER.info("Sending off job status event: {}", event);
        publisher.publish(MardukQueues.JOB_EVENT_QUEUE, message);
    }
}
