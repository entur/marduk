package no.rutebanken.marduk.routes.chouette.json;

import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.chouette.json.Parameters;
import no.rutebanken.marduk.routes.chouette.json.importer.GtfsImportParameters;
import no.rutebanken.marduk.routes.chouette.json.importer.RegtoppImportParameters;
import org.junit.Test;

import static net.javacrumbs.jsonunit.JsonAssert.assertJsonEquals;

public class ParametersTest {


    final String gtfsReferenceJson =  "{ \"parameters\": { \"gtfs-import\": {\"clean_repository\":\"0\",\"no_save\": \"0\", " +
            "\"user_name\": \"Chouette\", \"name\": \"test\", \"organisation_name\": \"Rutebanken\", \"referential_name\": " +
            "\"testDS\", \"object_id_prefix\": \"tds\", \"max_distance_for_commercial\": \"0\", \"split_id_on_dot\": \"0\", " +
            "\"ignore_last_word\": \"0\", \"ignore_end_chars\": \"0\"," +
            "\"route_type_id_scheme\": \"any\"," +
            " \"max_distance_for_connection_link\": \"0\", \"test\": false, \"update_stop_places\": false, \"import_stop_places\": false, \"keep_obsolete_lines\": true } } }";

    final String regtoppReferenceJson =  "{\"parameters\":{\"regtopp-import\":{\"name\":\"test\",\"clean_repository\":\"0\",\"no_save\":\"0\"," +
            "\"user_name\":\"Chouette\",\"organisation_name\":\"Rutebanken\",\"referential_name\":\"testDS\",\"object_id_prefix\":\"tds\"," +

            "\"references_type\":\"\",\"version\":\"R12\",\"coordinate_projection\":\"EPSG:32632\",\"calendar_strategy\":\"ADD\", \"test\": false, \"update_stop_places\": false, \"import_stop_places\": false, \"keep_obsolete_lines\": false, \"batch_parse\": true}}}";

    @Test
    public void createGtfsImportParameters() throws Exception {
        GtfsImportParameters importParameters = GtfsImportParameters.create("test", "tds", "testDS", "Rutebanken", "Chouette",false,false,false);
        assertJsonEquals(gtfsReferenceJson, importParameters.toJsonString());
    }

    @Test
    public void createRegtoppImportParameters() throws Exception {
        RegtoppImportParameters importParameters = RegtoppImportParameters.create("test", "tds", "testDS", "Rutebanken", "Chouette", "R12", "EPSG:32632","ADD",false,false,false,false,true);
        System.out.println(importParameters.toJsonString());
        assertJsonEquals(regtoppReferenceJson, importParameters.toJsonString());
    }

    @Test
    public void createRegtoppImportParametersWithValidation() throws Exception {
        RegtoppImportParameters importParameters = RegtoppImportParameters.create("test", "tds", "testDS", "Rutebanken", "Chouette", "R12", "EPSG:32632","ADD",false,false,true,false,true);
        System.out.println(importParameters.toJsonString());

    }

    @Test
    public void getNeptuneExportParameters() throws Exception {
        Provider provider = getProvider();
        Provider destProvider = new Provider();
        destProvider.chouetteInfo = new ChouetteInfo();
        destProvider.chouetteInfo.referential = "rb_ost";
        String neptuneExportParameters = Parameters.getTransferExportParameters(provider,destProvider);
        System.out.println(neptuneExportParameters);

    }

    private Provider getProvider() {
        ChouetteInfo chouetteInfo = new ChouetteInfo();
        chouetteInfo.id = 3L;
        chouetteInfo.organisation = "Ruter";
        chouetteInfo.regtoppVersion = "1.3";
        chouetteInfo.user = "rut";

        Provider provider = new Provider();
        provider.id = 2L;
        provider.chouetteInfo = chouetteInfo;
        return provider;
    }

}