package no.rutebanken.marduk.pubsub;

import no.rutebanken.marduk.pipeline.MardukMessage;
import org.entur.pubsub.base.AbstractEnturGooglePubSubConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MardukPubSubConsumerTest {

    private final Consumer consumer = new Consumer();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void aMessageWithoutACorrelationIdGetsOneOnTheMessageAndInTheMdc() {
        // The MDC half is what a handler setting only the header misses: onMessage has already read the
        // empty header, so every line the job then logs is unattributable.
        MardukMessage message = new MardukMessage();

        consumer.handle(message);

        String correlationId = message.getHeader(CORRELATION_ID, String.class);
        assertNotNull(correlationId);
        assertEquals(correlationId, MDC.get("correlationId"));
    }

    @Test
    void anExistingCorrelationIdIsKept() {
        MardukMessage message = new MardukMessage().setHeader(CORRELATION_ID, "corr");

        consumer.handle(message);

        assertEquals("corr", message.getHeader(CORRELATION_ID, String.class));
    }

    @Test
    void theSubscriberStatesComeFromTheLibrarysOwnList() throws NoSuchFieldException {
        // Read reflectively, because the library keeps the list private. A rename has to fail here rather
        // than leave the health check reporting nothing for every subscriber.
        assertNotNull(AbstractEnturGooglePubSubConsumer.class.getDeclaredField("subscribers"));

        assertEquals(Map.of(), consumer.subscriberStates(), "there is no subscriber before the context starts");
    }

    private static class Consumer extends MardukPubSubConsumer {

        @Override
        protected String destination() {
            return MardukQueues.PROCESS_FILE_QUEUE;
        }

        @Override
        protected void handle(MardukMessage message) {
            ensureCorrelationId(message);
        }
    }
}
