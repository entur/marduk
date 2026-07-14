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

package no.rutebanken.marduk.config;

import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import org.apache.camel.spi.StreamCachingStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Pins that camel.main.* properties actually bind (camel.springboot.* went silently dead on 4.18)
// and that the per-route .streamCaching() opt-in activates disk spooling for the upload caches.
class CamelMainConfigurationTest extends MardukRouteBuilderIntegrationTestBase {

    @Test
    void camelMainPropertiesAreBoundAndSpoolingIsActive() {
        context.start();

        assertEquals("Marduk", context.getName(), "camel.main.name not bound");
        assertFalse(context.isStreamCaching(), "global stream caching must stay off (per-route opt-in)");

        StreamCachingStrategy strategy = context.getStreamCachingStrategy();
        assertTrue(strategy.isEnabled(), "no route opted into stream caching");
        assertTrue(strategy.isSpoolEnabled(), "camel.main.streamCachingSpoolEnabled not bound");
        assertTrue(strategy.shouldSpoolCache(1024 * 1024), "1MB upload would buffer on heap instead of spooling");
    }
}
