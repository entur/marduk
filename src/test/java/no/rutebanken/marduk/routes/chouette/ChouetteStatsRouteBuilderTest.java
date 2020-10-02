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

package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;

class ChouetteStatsRouteBuilderTest {

    @Test
    void testProviderMatchingLevelFilterAndProviderId() {
        assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(1L, 3L), "level1", List.of("1", "2")));
        assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(1L, null), "level2", List.of("1", "2")));
        assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(2L, null), "all", List.of("1", "2")));
    }


    @Test
    void testProviderMatchingLevelFilter() {
        assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(1L, 3L), "level1", Collections.emptyList()));
        assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(2L, null), "level2", null));
        assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(1L, null), "all", null));
    }

    @Test
    void testProviderMatchingProviderId() {
        assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(1L, null), null, List.of("1", "2")));
        assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(2L, null), "all", List.of("1", "2")));
    }

    @Test
    void testProviderMatchingWhenNoFiltering() {
        assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(1L, null), null, null));
        assertTrue(new ChouetteStatsRouteBuilder().isMatch(provider(2L, null), "all", Collections.emptyList()));
    }

    @Test
    void testProviderNotMatchingWhenWrongLevelFiltering() {
        assertFalse(new ChouetteStatsRouteBuilder().isMatch(provider(1L, 3L), "level2", null));
        assertFalse(new ChouetteStatsRouteBuilder().isMatch(provider(2L, null), "level1", List.of("1", "2")));
    }

    @Test
    void testProviderNotMatchingNotInProviderIdList() {
        assertFalse(new ChouetteStatsRouteBuilder().isMatch(provider(1L, 4L), "all", List.of("2")));
        assertFalse(new ChouetteStatsRouteBuilder().isMatch(provider(3L, null), "level2", List.of("1", "2")));
    }


    Provider provider(Long id, Long migrateDataToProviderId) {
        Provider provider = new Provider();
        provider.id = id;
        provider.chouetteInfo = new ChouetteInfo();
        provider.chouetteInfo.migrateDataToProvider = migrateDataToProviderId;
        return provider;
    }
}
