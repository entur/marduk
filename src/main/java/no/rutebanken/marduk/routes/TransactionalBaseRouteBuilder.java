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

import org.springframework.beans.factory.annotation.Value;


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


    // TODO: Consider calling super.configure() to inherit the PubSub header
    //  interceptors (interceptFrom/interceptSendToEndpoint) from BaseRouteBuilder.
    //  Currently FileUploadRouteBuilder is the only subclass, and it publishes to
    //  ProcessFileQueue without the standard header propagation interceptors.
    //  This was not done now to avoid a behaviour change beyond adding MDC logging.
    //  If super.configure() is added, the duplicated MDC interceptor below can be removed.
    //
    // NOTE: The custom MDC keys (correlationId, codespace) are NOT propagated by
    //  Camel's built-in MDC logging (setUseMDCLogging) across thread boundaries.
    //  If routes introduce .threads(), .wireTap(), or async processing, these keys
    //  will be lost on the new threads. All current PubSub routes are synchronous.
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

        configureMdcLogging();

    }
}
