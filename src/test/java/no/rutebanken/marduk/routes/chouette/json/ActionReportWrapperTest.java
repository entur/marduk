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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ActionReportWrapperTest {

    @Test
    public void testErrorResult() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        StringReader reader = new StringReader(ERROR_RESPONSE);
        ActionReportWrapper actionReport = mapper.readValue(reader, ActionReportWrapper.class);
        assertEquals("NOK", actionReport.actionReport.result);
        assertTrue((actionReport.isFinalised()));
    }


    public static final String ERROR_RESPONSE = "{\"action_report\": {\n" +
                                                        "  \"progression\":    {\n" +
                                                        "      \"current_step\": 3,\n" +
                                                        "      \"steps_count\": 3,\n" +
                                                        "      \"steps\": [\n" +
                                                        "        {\n" +
                                                        "          \"step\": \"INITIALISATION\",\n" +
                                                        "          \"total\": 2,\n" +
                                                        "          \"realized\": 2\n" +
                                                        "        },\n" +
                                                        "        {\n" +
                                                        "          \"step\": \"PROCESSING\",\n" +
                                                        "          \"total\": 1,\n" +
                                                        "          \"realized\": 1\n" +
                                                        "        },\n" +
                                                        "        {\n" +
                                                        "          \"step\": \"FINALISATION\",\n" +
                                                        "          \"total\": 4,\n" +
                                                        "          \"realized\": 4\n" +
                                                        "        }\n" +
                                                        "      ]\n" +
                                                        "    },\n" +
                                                        "  \"result\": \"NOK\",\n" +
                                                        "  \"failure\":    {\n" +
                                                        "      \"code\": \"INVALID_DATA\",\n" +
                                                        "      \"description\": \"MISSING_FILE agency.txt\"\n" +
                                                        "    },\n" +
                                                        "  \"files\": [\n" +
                                                        "    {\n" +
                                                        "      \"name\": \"stops.txt\",\n" +
                                                        "      \"status\": \"IGNORED\",\n" +
                                                        "      \"io_type\": \"OUTPUT\",\n" +
                                                        "      \"check_point_error_count\": 0,\n" +
                                                        "      \"check_point_warning_count\": 0,\n" +
                                                        "      \"check_point_info_count\": 0\n" +
                                                        "    },\n" +
                                                        "    {\n" +
                                                        "      \"name\": \"agency.txt\",\n" +
                                                        "      \"status\": \"ERROR\",\n" +
                                                        "      \"io_type\": \"INPUT\",\n" +
                                                        "      \"errors\": [\n" +
                                                        "        {\n" +
                                                        "          \"code\": \"FILE_NOT_FOUND\",\n" +
                                                        "          \"description\": \"The file \\\"agency.txt\\\" must be provided (rule 1-GTFS-Common-1)\"\n" +
                                                        "        }\n" +
                                                        "      ],\n" +
                                                        "      \"check_point_errors\": [0],\n" +
                                                        "      \"check_point_error_count\": 1,\n" +
                                                        "      \"check_point_warning_count\": 0,\n" +
                                                        "      \"check_point_info_count\": 0\n" +
                                                        "    }\n" +
                                                        "  ],\n" +
                                                        "  \"objects\": [\n" +
                                                        "    {\n" +
                                                        "      \"type\": \"connection_link\",\n" +
                                                        "      \"description\": \"connection links\",\n" +
                                                        "      \"objectid\": \"merged\",\n" +
                                                        "      \"status\": \"OK\",\n" +
                                                        "      \"io_type\": \"OUTPUT\",\n" +
                                                        "      \"stats\":      {\n" +
                                                        "        \"connection_link\": 0\n" +
                                                        "      },\n" +
                                                        "      \"check_point_error_count\": 0,\n" +
                                                        "      \"check_point_warning_count\": 0,\n" +
                                                        "      \"check_point_info_count\": 0\n" +
                                                        "    },\n" +
                                                        "    {\n" +
                                                        "      \"type\": \"company\",\n" +
                                                        "      \"description\": \"companies\",\n" +
                                                        "      \"objectid\": \"merged\",\n" +
                                                        "      \"status\": \"OK\",\n" +
                                                        "      \"io_type\": \"OUTPUT\",\n" +
                                                        "      \"stats\":      {\n" +
                                                        "        \"company\": 0\n" +
                                                        "      },\n" +
                                                        "      \"check_point_error_count\": 0,\n" +
                                                        "      \"check_point_warning_count\": 0,\n" +
                                                        "      \"check_point_info_count\": 0\n" +
                                                        "    },\n" +
                                                        "    {\n" +
                                                        "      \"type\": \"timetable\",\n" +
                                                        "      \"description\": \"calendars\",\n" +
                                                        "      \"objectid\": \"merged\",\n" +
                                                        "      \"status\": \"OK\",\n" +
                                                        "      \"io_type\": \"OUTPUT\",\n" +
                                                        "      \"stats\":      {\n" +
                                                        "        \"timetable\": 1\n" +
                                                        "      },\n" +
                                                        "      \"check_point_error_count\": 0,\n" +
                                                        "      \"check_point_warning_count\": 0,\n" +
                                                        "      \"check_point_info_count\": 0\n" +
                                                        "    },\n" +
                                                        "    {\n" +
                                                        "      \"type\": \"stop_area\",\n" +
                                                        "      \"description\": \"stop areas\",\n" +
                                                        "      \"objectid\": \"merged\",\n" +
                                                        "      \"status\": \"OK\",\n" +
                                                        "      \"io_type\": \"OUTPUT\",\n" +
                                                        "      \"stats\":      {\n" +
                                                        "        \"stop_area\": 5\n" +
                                                        "      },\n" +
                                                        "      \"check_point_error_count\": 0,\n" +
                                                        "      \"check_point_warning_count\": 0,\n" +
                                                        "      \"check_point_info_count\": 0\n" +
                                                        "    }\n" +
                                                        "  ],\n" +
                                                        "  \"collections\": [\n" +
                                                        "    {\n" +
                                                        "      \"type\": \"line\",\n" +
                                                        "      \"objects\": [\n" +
                                                        "        {\n" +
                                                        "          \"type\": \"line\",\n" +
                                                        "          \"description\": \"Flexx 809 Gamvik - Mehamn\",\n" +
                                                        "          \"objectid\": \"FIN:FlexibleLine:809\",\n" +
                                                        "          \"status\": \"ERROR\",\n" +
                                                        "          \"io_type\": \"OUTPUT\",\n" +
                                                        "          \"stats\":          {\n" +
                                                        "            \"route\": 1,\n" +
                                                        "            \"journey_pattern\": 1,\n" +
                                                        "            \"vehicle_journey\": 2,\n" +
                                                        "            \"line\": 0,\n" +
                                                        "            \"interchange\": 0\n" +
                                                        "          },\n" +
                                                        "          \"errors\": [\n" +
                                                        "            {\n" +
                                                        "              \"code\": \"NO_DATA_ON_PERIOD\",\n" +
                                                        "              \"description\": \"no data on period\"\n" +
                                                        "            }\n" +
                                                        "          ],\n" +
                                                        "          \"check_point_error_count\": 0,\n" +
                                                        "          \"check_point_warning_count\": 0,\n" +
                                                        "          \"check_point_info_count\": 0\n" +
                                                        "        }\n" +
                                                        "      ],\n" +
                                                        "      \"stats\":      {\n" +
                                                        "        \"route\": 1,\n" +
                                                        "        \"journey_pattern\": 1,\n" +
                                                        "        \"vehicle_journey\": 2,\n" +
                                                        "        \"line\": 0,\n" +
                                                        "        \"interchange\": 0\n" +
                                                        "      }\n" +
                                                        "    }\n" +
                                                        "  ]\n" +
                                                        "}}";
}
