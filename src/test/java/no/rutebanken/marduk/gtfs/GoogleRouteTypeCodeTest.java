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

package no.rutebanken.marduk.gtfs;

import static org.junit.jupiter.api.Assertions.*;

import no.rutebanken.marduk.gtfs.GoogleRouteTypeCode;
import org.junit.jupiter.api.Test;

class GoogleRouteTypeCodeTest {


    @Test
    void supportedVersionsAreReturnedUnchanged() {
        assertMapping(0, 0);
        assertMapping(6, 6);
        assertMapping(100, 100);
        assertMapping(101, 101);
        assertMapping(200, 200);
        assertMapping(401, 401);
    }

    @Test
    void unsupportedVersionsWithExplicitMappingsAreMapped() {
        assertMapping(300, 100);
        assertMapping(500, 401);
        assertMapping(600, 402);
        assertMapping(1200, 1000);
        assertMapping(1500, 1501);
    }

    @Test
    void unsupportedVersionsWithoutExplicitMappingWithSupportedBaseTypeAreReturnedAsBaseType() {
        assertMapping(117, 100);
        assertMapping(118, 100);
        assertMapping(1021, 1000);
        assertMapping(1703, 1700);
    }

    @Test
    void unsupportedVersionsWithoutExplicitMappingWithUnsupportedBaseTypesWithExplicitMappingAreReturnedAsBaseTypesMapping() {
        assertMapping(1502, 1501);
        assertMapping(1506, 1501);
    }

    @Test
    void unsupportedVersionsWithNoMappingAreReturnedAsMisc() {
        assertMapping(20000, 1700);
        assertMapping(1600, 1700);
        assertMapping(1601, 1700);
        assertMapping(9, 1700);
    }

    private void assertMapping(int org, int expected) {
        assertEquals(expected, GoogleRouteTypeCode.toGoogleSupportedRouteTypeCode(org));
    }
}
