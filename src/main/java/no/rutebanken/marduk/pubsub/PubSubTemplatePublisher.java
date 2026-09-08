package no.rutebanken.marduk.pubsub;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.pipeline.MardukMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ExecutionException;

@Component
public class PubSubTemplatePublisher implements MardukPubSubPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(PubSubTemplatePublisher.class);

    private final PubSubTemplate pubSubTemplate;
    private final MardukQueues queues;

    public PubSubTemplatePublisher(PubSubTemplate pubSubTemplate, MardukQueues queues) {
        this.pubSubTemplate = pubSubTemplate;
        this.queues = queues;
    }

    @Override
    public void publish(String destination, MardukMessage message) {
        String topic = queues.topic(destination);
        Map<String, String> attributes = PubSubAttributes.toAttributes(message);
        // Bodies on the wire are text throughout: an empty string, a JobEvent as JSON, a codespace, or a
        // comma-separated file list. A null body publishes as empty, which is what setBody(constant(""))
        // produced before.
        String body = message.getBody(String.class);
        try {
            // Blocking on the publish is deliberate. A step that reports a status and then continues must
            // not be able to overtake its own status message, and a failure has to surface here so the
            // caller can nack rather than being logged on a publisher thread nobody watches.
            pubSubTemplate.publish(topic, body == null ? "" : body, attributes).get();
            LOGGER.debug("Published to {} with {} attribute(s)", destination, attributes.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MardukException("Interrupted while publishing to " + destination, e);
        } catch (ExecutionException e) {
            throw new MardukException("Failed to publish to " + destination, e);
        }
    }
}
