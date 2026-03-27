/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.routes;

import no.rutebanken.marduk.Constants;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;


/**
 * Base route builder for transactional routes.
 */
public abstract class TransactionalBaseRouteBuilder extends BaseRouteBuilder {

    @Value("${quartz.lenient.fire.time.ms:180000}")
    private int lenientFireTimeMs;

    @Value("${marduk.camel.redelivery.max:3}")
    private int maxRedelivery;

    @Value("${marduk.camel.redelivery.delay:5000}")
    private int redeliveryDelay;

    @Value("${marduk.camel.redelivery.backoff.multiplier:3}")
    private int backOffMultiplier;


    @Override
    public void configure() {
        errorHandler(springTransactionErrorHandler()
                .redeliveryDelay(redeliveryDelay)
                .maximumRedeliveries(maxRedelivery)
                .onRedelivery(this::logRedelivery)
                .useExponentialBackOff()
                .backOffMultiplier(backOffMultiplier)
                .logExhausted(true)
                .logRetryStackTrace(true));

        // Copy PubSub attributes into Camel headers.
        interceptFrom(".*google-pubsub:.*").process(exchange -> {
            Map<String, String> pubSubAttributes = exchange.getIn().getHeader(GooglePubsubConstants.ATTRIBUTES, java.util.Map.class);
            if (pubSubAttributes != null) {
                pubSubAttributes.entrySet()
                        .stream()
                        .filter(entry -> !entry.getKey().startsWith("CamelGooglePubsub"))
                        .forEach(entry -> exchange.getIn().setHeader(entry.getKey(), entry.getValue()));
            }
        });

        // Copy correlation ID and codespace into SLF4J MDC for structured logging.
        interceptFrom(".*").process(exchange -> {
            String correlationId = exchange.getIn().getHeader(Constants.CORRELATION_ID, String.class);
            if (correlationId != null) {
                MDC.put("correlationId", correlationId);
            }
            String codespace = exchange.getIn().getHeader(Constants.DATASET_REFERENTIAL, String.class);
            if (codespace == null) {
                codespace = exchange.getIn().getHeader(Constants.CHOUETTE_REFERENTIAL, String.class);
            }
            if (codespace != null) {
                MDC.put("codespace", codespace);
            }
        });

        configureMdcLogging();

    }
}
