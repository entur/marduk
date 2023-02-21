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

import java.io.StringReader;

import com.fasterxml.jackson.databind.ObjectReader;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActionReportWrapperTest {

    @Test
    void testErrorResult() throws Exception {
        ObjectReader objectReader = ObjectMapperFactory.getSharedObjectMapper().readerFor(ActionReportWrapper.class);
        StringReader reader = new StringReader(ERROR_RESPONSE);
        ActionReportWrapper actionReport = objectReader.readValue(reader);
        assertEquals("NOK", actionReport.actionReport.result);
        assertTrue((actionReport.isFinalised()));
    }


    public static final String ERROR_RESPONSE = """
            {"action_report": {
              "progression":    {
                  "current_step": 3,
                  "steps_count": 3,
                  "steps": [
                    {
                      "step": "INITIALISATION",
                      "total": 2,
                      "realized": 2
                    },
                    {
                      "step": "PROCESSING",
                      "total": 1,
                      "realized": 1
                    },
                    {
                      "step": "FINALISATION",
                      "total": 4,
                      "realized": 4
                    }
                  ]
                },
              "result": "NOK",
              "failure":    {
                  "code": "INVALID_DATA",
                  "description": "MISSING_FILE agency.txt"
                },
              "files": [
                {
                  "name": "stops.txt",
                  "status": "IGNORED",
                  "io_type": "OUTPUT",
                  "check_point_error_count": 0,
                  "check_point_warning_count": 0,
                  "check_point_info_count": 0
                },
                {
                  "name": "agency.txt",
                  "status": "ERROR",
                  "io_type": "INPUT",
                  "errors": [
                    {
                      "code": "FILE_NOT_FOUND",
                      "description": "The file \\"agency.txt\\" must be provided (rule 1-GTFS-Common-1)"
                    }
                  ],
                  "check_point_errors": [0],
                  "check_point_error_count": 1,
                  "check_point_warning_count": 0,
                  "check_point_info_count": 0
                }
              ],
              "objects": [
                {
                  "type": "connection_link",
                  "description": "connection links",
                  "objectid": "merged",
                  "status": "OK",
                  "io_type": "OUTPUT",
                  "stats":      {
                    "connection_link": 0
                  },
                  "check_point_error_count": 0,
                  "check_point_warning_count": 0,
                  "check_point_info_count": 0
                },
                {
                  "type": "company",
                  "description": "companies",
                  "objectid": "merged",
                  "status": "OK",
                  "io_type": "OUTPUT",
                  "stats":      {
                    "company": 0
                  },
                  "check_point_error_count": 0,
                  "check_point_warning_count": 0,
                  "check_point_info_count": 0
                },
                {
                  "type": "timetable",
                  "description": "calendars",
                  "objectid": "merged",
                  "status": "OK",
                  "io_type": "OUTPUT",
                  "stats":      {
                    "timetable": 1
                  },
                  "check_point_error_count": 0,
                  "check_point_warning_count": 0,
                  "check_point_info_count": 0
                },
                {
                  "type": "stop_area",
                  "description": "stop areas",
                  "objectid": "merged",
                  "status": "OK",
                  "io_type": "OUTPUT",
                  "stats":      {
                    "stop_area": 5
                  },
                  "check_point_error_count": 0,
                  "check_point_warning_count": 0,
                  "check_point_info_count": 0
                }
              ],
              "collections": [
                {
                  "type": "line",
                  "objects": [
                    {
                      "type": "line",
                      "description": "Flexx 809 Gamvik - Mehamn",
                      "objectid": "FIN:FlexibleLine:809",
                      "status": "ERROR",
                      "io_type": "OUTPUT",
                      "stats":          {
                        "route": 1,
                        "journey_pattern": 1,
                        "vehicle_journey": 2,
                        "line": 0,
                        "interchange": 0
                      },
                      "errors": [
                        {
                          "code": "NO_DATA_ON_PERIOD",
                          "description": "no data on period"
                        }
                      ],
                      "check_point_error_count": 0,
                      "check_point_warning_count": 0,
                      "check_point_info_count": 0
                    }
                  ],
                  "stats":      {
                    "route": 1,
                    "journey_pattern": 1,
                    "vehicle_journey": 2,
                    "line": 0,
                    "interchange": 0
                  }
                }
              ]
            }}""";
}
