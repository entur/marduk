package no.rutebanken.marduk.routes.chouette.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.StringReader;
import java.io.StringWriter;

import static net.javacrumbs.jsonunit.JsonAssert.assertJsonEquals;

public class ImportResponseTest {

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
        ImportResponse importResponse = mapper.readValue(reader, ImportResponse.class);
        StringWriter writer = new StringWriter();
        mapper.writeValue(writer, importResponse);
        assertJsonEquals(inputJson, writer.toString());
    }

}