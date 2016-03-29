package no.rutebanken.marduk.routes.chouette.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.StringWriter;

import static net.javacrumbs.jsonunit.JsonAssert.assertJsonEquals;

public class ImportParametersTest {

    String referenceJson =  "{ \"parameters\": { \"gtfs-import\": {\"clean_repository\": \"true\", \"no_save\": \"0\", " +
            "\"user_name\": \"Chouette\", \"name\": \"test\", \"organisation_name\": \"Rutebanken\", \"referential_name\": " +
            "\"testDS\", \"object_id_prefix\": \"tds\", \"max_distance_for_commercial\": \"0\", " +
            "\"ignore_last_word\": \"0\", \"ignore_end_chars\": \"0\"," +
            "\"route_type_id_scheme\": \"any\"," +
            " \"max_distance_for_connection_link\": \"0\", \"references_type\": \"\" } } }";

    @Test
    public void createInputJson() throws Exception {
        ImportParameters.GtfsImport gtfsImport = new ImportParameters.GtfsImport("test", "tds", "testDS", "Rutebanken", "Chouette", true);
        ImportParameters.Parameters parameters = new ImportParameters.Parameters(gtfsImport);
        ImportParameters importParameters = new ImportParameters(parameters);
        ObjectMapper mapper = new ObjectMapper();
        StringWriter writer = new StringWriter();
        mapper.writeValue(writer, importParameters);
        assertJsonEquals(referenceJson, writer.toString());
    }

}