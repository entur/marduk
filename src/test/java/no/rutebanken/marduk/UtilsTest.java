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

package no.rutebanken.marduk;

import org.junit.Test;

import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class UtilsTest {

    @Test
    public void testGetJobId(){
        String locationUrl = "http://localhost:8180/chouette_iev/referentials/avinor/scheduled_jobs/2321";
        assertEquals(Long.valueOf(2321), Utils.getLastPathElementOfUrl(locationUrl));
    }

    @Test (expected = IllegalArgumentException.class)
    public void testGetJobIdWithNull(){
        Utils.getLastPathElementOfUrl(null);
    }

    @Test
    public void testGetHttp4(){
        String url = "http://localhost:8180/chouette_iev/referentials/avinor";
        assertEquals("http4://localhost:8180/chouette_iev/referentials/avinor", Utils.getHttp4(url));
    }

    @Test (expected = IllegalArgumentException.class)
    public void testGetHttp4WithNull(){
        Utils.getHttp4(null);
    }

    @Test
    public void getOtpVersion(){
        assertThat(Utils.getOtpVersion(), matchesPattern("\\d+\\.\\d+\\.\\d+.*"));
    }

}
