package no.rutebanken.marduk.pubsub;

import no.rutebanken.marduk.pipeline.MardukMessage;

/**
 * Publishes a message to a PubSub destination.
 *
 * <p>An interface so tests can record what would have been published instead of standing up an emulator.
 */
public interface MardukPubSubPublisher {

    /**
     * Publishes the message body and, as attributes, every header {@link PubSubAttributes} allows out.
     *
     * @param destination one of the {@link MardukQueues} names, unqualified; the project is resolved here
     */
    void publish(String destination, MardukMessage message);
}
