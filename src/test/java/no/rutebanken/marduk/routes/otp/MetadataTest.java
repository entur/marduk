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

package no.rutebanken.marduk.routes.otp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Date;

public class MetadataTest {

    @Test
    public void getJson() throws IOException {
        String filename = "unknown_file_name_flag";

        Metadata.Status status = Metadata.Status.NOK;
        String json = new Metadata("OSM file update status.",
                        filename,
                        new Date(),
                        status,
                        Metadata.Action.OSM_NORWAY_UPDATED).getJson();
        assertTrue(json.contains("OSM"));
    }

}