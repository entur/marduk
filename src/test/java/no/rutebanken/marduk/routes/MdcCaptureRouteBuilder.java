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

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Test route that captures MDC values into message headers for assertion.
 */
@Component
public class MdcCaptureRouteBuilder extends BaseRouteBuilder {

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:testMdc")
                .process(exchange -> {
                    exchange.getIn().setHeader("capturedCorrelationId", MDC.get("correlationId"));
                    exchange.getIn().setHeader("capturedCodespace", MDC.get("codespace"));
                })
                .to("mock:mdcResult")
                .routeId("test-mdc-capture");

        from("direct:testMdcCleanup")
                .log("Processing exchange for MDC cleanup test")
                .to("mock:mdcCleanupResult")
                .routeId("test-mdc-cleanup");
    }
}
