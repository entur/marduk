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

package no.rutebanken.marduk.routes.chouette.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.StringReader;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class JobResponseTest {

    String inputJson = "{\"id\":130,\"referential\":\"tds\",\"action\":\"importer\",\"type\":\"gtfs\"," +
            "\"created\":1450177618732,\"updated\":1450177618732,\"status\":\"SCHEDULED\"," +
            "\"links\":[{\"rel\":\"parameters\",\"href\":\"http://chouette:8080/chouette_iev/referentials/tds/data/130/parameters.json\"," +
            "\"type\":\"application/json\",\"method\":\"get\"},{\"rel\":\"action_params\"," +
            "\"href\":\"http://chouette:8080/chouette_iev/referentials/tds/data/130/action_parameters.json\"," +
            "\"type\":\"application/json\",\"method\":\"get\"},{\"rel\":\"data\",\"href\":" +
            "\"http://chouette:8080/chouette_iev/referentials/tds/data/130/20151215110641-rpsrute.zip\"," +
            "\"type\":\"application/octet-stream\",\"method\":\"get\"},{\"rel\":\"cancel\"," +
            "\"href\":\"http://chouette:8080/chouette_iev/referentials/tds/scheduled_jobs/130\"," +
            "\"type\":\"application/json\",\"method\":\"delete\"}],\"action_parameters\":{\"object_id_prefix\":\"tds\"," +
            "\"max_distance_for_connection_link\":0,\"max_distance_for_commercial\":0,\"ignore_end_chars\":0," +
            "\"ignore_last_word\":false,\"references_type\":\"\",\"name\":\"test\",\"user_name\":\"Chouette\"," +
            "\"organisation_name\":\"Rutebanken\",\"referential_name\":\"testDS\",\"no_save\":false,\"clean_repository\":false}}";

    @Test
    public void createInputJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        StringReader reader = new StringReader(inputJson);
        JobResponseWithLinks jobResponse = mapper.readValue(reader, JobResponseWithLinks.class);
        assertEquals(Status.SCHEDULED, jobResponse.status);
        assertEquals("http://chouette:8080/chouette_iev/referentials/tds/data/130/parameters.json",
                jobResponse.links.stream().filter(li -> li.rel.equals("parameters")).collect(Collectors.toList()).get(0).href);
    }

}