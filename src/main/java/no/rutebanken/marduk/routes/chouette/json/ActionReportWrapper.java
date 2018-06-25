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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ActionReportWrapper {

    /*

 {"action_report": {
  "progression": {
    "current_step": 1,
    "steps_count": 3,
    "steps": [
      {
        "step": "INITIALISATION",
        "total": 5,
        "realized": 4
      },
      {
        "step": "PROCESSING",
        "total": 1,
        "realized": 0
      },
      {
        "step": "FINALISATION",
        "total": 1,
        "realized": 0
      }
    ]
  },
  "result": "NOK",
  "zip_file": {
    "name": "20160104080334-000111-gtfs.zip",
    "status": "OK"
  },
  "files": [
    {
      "name": "calendar_dates.txt",
      "status": "IGNORED"
    },
    {
      "name": "trips.txt",
      "status": "IGNORED"
    },
    {
      "name": "calendar.txt",
      "status": "IGNORED"
    },
    {
      "name": "agency.txt",
      "status": "ERROR",
      "errors": [{
        "code": "READ_ERROR",
        "description": "Il y a des erreurs dans ce fichier."
      }]
    },
    {
      "name": "stop_times.txt",
      "status": "IGNORED"
    },
    {
      "name": "stops.txt",
      "status": "IGNORED"
    },
    {
      "name": "routes.txt",
      "status": "ERROR",
      "errors": [{
        "code": "READ_ERROR",
        "description": "Il y a des erreurs dans ce fichier."
      }]
    }
  ],
  "stats": {
    "line_count": 0,
    "route_count": 0,
    "connection_link_count": 0,
    "time_table_count": 0,
    "stop_area_count": 0,
    "access_point_count": 0,
    "vehicle_journey_count": 0,
    "journey_pattern_count": 0
  },
  "failure": {
    "code": "INVALID_DATA",
    "description": "INVALID_FORMAT \/opt\/jboss\/referentials\/tds1\/data\/62\/input\/routes.txt"
  }
}}

     */

    @JsonProperty("action_report")
    public ActionReport actionReport;

    /**
     * Check if report signals a completed job.
     * <p>
     * Job is completed if status is changed from default values (OK) or if finalisation step has been completed.
     * <p>
     * This check is needed because db job in chouette is somtimes given status 'TERMINATED' before action report has been written.
     */
    public boolean isFinalised() {

        if (actionReport != null) {
            if (actionReport.result != null && !"OK".equals(actionReport.result)) {
                return true;
            }
            if (actionReport.progression != null && actionReport.progression.steps != null) {
                return actionReport.progression.steps.stream().anyMatch(s -> "FINALISATION".equals(s.step) && s.total == s.realized);
            }
        }
        return false;
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ActionReport {
        public String result;

        public Progression progression;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Progression {
        public List<Step> steps;

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Step {
        public String step;
        public int total;
        public int realized;
    }


}
