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

package no.rutebanken.marduk.routes.status;

import no.rutebanken.marduk.Constants;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobEventTest {

    private static Exchange exchange() {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setHeader(Constants.PROVIDER_ID, "1");
        exchange.getIn().setHeader(Constants.CORRELATION_ID, "correlation");
        return exchange;
    }

    /**
     * When an explicit event time is supplied (e.g. the emit time relayed from servicelinker),
     * build() must use it verbatim rather than overwriting it with the current time.
     */
    @Test
    void explicitEventTimeIsUsed() {
        Instant emitTime = Instant.parse("2026-06-03T10:15:30Z");

        JobEvent event = JobEvent.providerJobBuilder(exchange())
                .timetableAction(JobEvent.TimetableAction.LINKING)
                .state(JobEvent.State.OK)
                .eventTime(emitTime)
                .build();

        assertEquals(emitTime, event.getEventTime());
    }

    /**
     * When no event time is supplied (e.g. a message from an older servicelinker without the
     * emit-time header), build() must fall back to the current time so the field is never null.
     */
    @Test
    void eventTimeDefaultsToNowWhenNotSet() {
        Instant before = Instant.now();

        JobEvent event = JobEvent.providerJobBuilder(exchange())
                .timetableAction(JobEvent.TimetableAction.LINKING)
                .state(JobEvent.State.STARTED)
                .build();

        Instant after = Instant.now();

        assertNotNull(event.getEventTime());
        assertTrue(!event.getEventTime().isBefore(before) && !event.getEventTime().isAfter(after),
                "Expected event time to default to ~now, but was " + event.getEventTime());
    }

    /**
     * A null event time must also trigger the fallback (the route passes null when the emit-time
     * header is missing or unparseable).
     */
    @Test
    void nullEventTimeFallsBackToNow() {
        Instant before = Instant.now();

        JobEvent event = JobEvent.providerJobBuilder(exchange())
                .timetableAction(JobEvent.TimetableAction.LINKING)
                .state(JobEvent.State.OK)
                .eventTime(null)
                .build();

        assertNotNull(event.getEventTime());
        assertTrue(!event.getEventTime().isBefore(before), "Expected fallback to now(), but was " + event.getEventTime());
    }
}
