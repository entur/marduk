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

/**
 * JSON mapping for Chouette ActionReport.
 *
 * JSON Structure example:
 *
 * {"action_report": {
 *   "progression": {
 *     "current_step": 1,
 *     "steps_count": 3,
 *     "steps": [
 *       {
 *         "step": "INITIALISATION",
 *         "total": 5,
 *         "realized": 4
 *       },
 *       {
 *         "step": "PROCESSING",
 *         "total": 1,
 *         "realized": 0
 *       },
 *       {
 *         "step": "FINALISATION",
 *         "total": 1,
 *         "realized": 0
 *       }
 *     ]
 *   },
 *   "result": "NOK",
 *   "zip_file": {
 *     "name": "20160104080334-000111-gtfs.zip",
 *     "status": "OK"
 *   },
 *   "files": [
 *     {
 *       "name": "calendar_dates.txt",
 *       "status": "IGNORED"
 *     },
 *     {
 *       "name": "trips.txt",
 *       "status": "IGNORED"
 *     },
 *     {
 *       "name": "calendar.txt",
 *       "status": "IGNORED"
 *     },
 *     {
 *       "name": "agency.txt",
 *       "status": "ERROR",
 *       "errors": [{
 *         "code": "READ_ERROR",
 *         "description": "Il y a des erreurs dans ce fichier."
 *       }]
 *     },
 *     {
 *       "name": "stop_times.txt",
 *       "status": "IGNORED"
 *     },
 *     {
 *       "name": "stops.txt",
 *       "status": "IGNORED"
 *     },
 *     {
 *       "name": "routes.txt",
 *       "status": "ERROR",
 *       "errors": [{
 *         "code": "READ_ERROR",
 *         "description": "Il y a des erreurs dans ce fichier."
 *       }]
 *     }
 *   ],
 *   "stats": {
 *     "line_count": 0,
 *     "route_count": 0,
 *     "connection_link_count": 0,
 *     "time_table_count": 0,
 *     "stop_area_count": 0,
 *     "access_point_count": 0,
 *     "vehicle_journey_count": 0,
 *     "journey_pattern_count": 0
 *   },
 *   "failure": {
 *     "code": "INVALID_DATA",
 *     "description": "INVALID_FORMAT \/opt\/jboss\/referentials\/tds1\/data\/62\/input\/routes.txt"
 *   }
 * }}
 *
 */
public class ActionReportWrapper {

    @JsonProperty("action_report")
    private ActionReport actionReport;

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

    public ActionReport getActionReport() {
        return actionReport;
    }

    public ActionReportWrapper setActionReport(ActionReport actionReport) {
        this.actionReport = actionReport;
        return this;
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ActionReport {
        private String result;

        private Progression progression;

        private Failure failure;

        public String getResult() {
            return result;
        }

        public ActionReport setResult(String result) {
            this.result = result;
            return this;
        }

        public Progression getProgression() {
            return progression;
        }

        public ActionReport setProgression(Progression progression) {
            this.progression = progression;
            return this;
        }

        public Failure getFailure() {
            return failure;
        }

        public ActionReport setFailure(Failure failure) {
            this.failure = failure;
            return this;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Progression {
        private List<Step> steps;

        public List<Step> getSteps() {
            return steps;
        }

        public Progression setSteps(List<Step> steps) {
            this.steps = steps;
            return this;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Step {
        private String step;
        private int total;
        private int realized;

        public String getStep() {
            return step;
        }

        public Step setStep(String step) {
            this.step = step;
            return this;
        }

        public int getTotal() {
            return total;
        }

        public Step setTotal(int total) {
            this.total = total;
            return this;
        }

        public int getRealized() {
            return realized;
        }

        public Step setRealized(int realized) {
            this.realized = realized;
            return this;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Failure {
        private String code;

        public String getCode() {
            return code;
        }

        public Failure setCode(String code) {
            this.code = code;
            return this;
        }
    }


}
