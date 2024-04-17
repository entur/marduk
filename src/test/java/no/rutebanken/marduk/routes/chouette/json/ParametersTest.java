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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.jsonunit.core.Option;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import no.rutebanken.marduk.routes.chouette.json.importer.GtfsImportParameters;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static net.javacrumbs.jsonunit.JsonAssert.assertJsonEquals;
import static net.javacrumbs.jsonunit.JsonAssert.when;
import static no.rutebanken.marduk.TestConstants.CHOUETTE_REFERENTIAL_RUT;
import static no.rutebanken.marduk.TestConstants.PROVIDER_ID_RUT;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ParametersTest {


    private static final String GTFS_REFERENCE_JSON =  "{ \"parameters\": { \"gtfs-import\": {\"clean_repository\":\"0\",\"no_save\": \"0\", " +
            "\"user_name\": \"Chouette\", \"name\": \"test\", \"organisation_name\": \"Rutebanken\", \"referential_name\": " +
            "\"testDS\", \"object_id_prefix\": \"tds\", \"max_distance_for_commercial\": \"0\", \"split_id_on_dot\": \"0\", " +
            "\"ignore_last_word\": \"0\", \"ignore_end_chars\": \"0\"," +
            "\"route_type_id_scheme\": \"any\"," +
            "\"parse_connection_links\": false," +
                " \"max_distance_for_connection_link\": \"0\", \"test\": false, \"stop_area_remote_id_mapping\": true, \"stop_area_import_mode\": \"CREATE_NEW\", \"keep_obsolete_lines\": true, \"generate_missing_route_sections_for_modes\": [\"water\",\"bus\"] } } }";
    @Test
    void testGtfsImportParameters() {
        GtfsImportParameters importParameters = GtfsImportParameters.create("test", "tds", "testDS", "Rutebanken", "Chouette",false,false,true, true, Set.of("water","bus"));
        assertJsonEquals(GTFS_REFERENCE_JSON, importParameters.toJsonString(), when(Option.IGNORING_ARRAY_ORDER));
    }

    @Test
    void testGtfsExportParameters() throws JsonProcessingException {
        Provider provider = getProvider();
        String gtfsExportParametersAsString = Parameters.getGtfsExportParameters(provider);
        final ObjectMapper mapper = ObjectMapperFactory.getSharedObjectMapper().copy();
        JsonNode gtfsExportParametersAsJson = mapper.readTree(gtfsExportParametersAsString);
        JsonNode providerIdNode = gtfsExportParametersAsJson.path("parameters").path("gtfs-export").path("referential_name");
        assertEquals(provider.getChouetteInfo().getReferential(), providerIdNode.asText(), "The referential_name parameter should reference the provider referential");
    }

    @Test
    void testTransferExportParameters() throws JsonProcessingException {
        Provider provider = getProvider();
        Provider destProvider = new Provider();
        destProvider.setChouetteInfo(new ChouetteInfo());
        String targetReferentialName = "rb_ost";
        destProvider.getChouetteInfo().setReferential(targetReferentialName);
        String transferExportParametersAsString = Parameters.getTransferExportParameters(provider, destProvider);

        final ObjectMapper mapper = ObjectMapperFactory.getSharedObjectMapper().copy();
        JsonNode transferExportParametersasJson = mapper.readTree(transferExportParametersAsString);
        JsonNode targetReferentialNode = transferExportParametersasJson.path("parameters").path("transfer-export").path("dest_referential_name");
        assertEquals(targetReferentialName, targetReferentialNode.asText(), "The transfer parameter should reference the target referential");
    }

    private static Provider getProvider() {
        ChouetteInfo chouetteInfo = new ChouetteInfo();
        chouetteInfo.setId(3L);
        chouetteInfo.setOrganisation("Ruter");
        chouetteInfo.setUser(CHOUETTE_REFERENTIAL_RUT);
        chouetteInfo.setReferential(CHOUETTE_REFERENTIAL_RUT);

        Provider provider = new Provider();
        provider.setId(PROVIDER_ID_RUT);
        provider.setChouetteInfo(chouetteInfo);
        return provider;
    }

}