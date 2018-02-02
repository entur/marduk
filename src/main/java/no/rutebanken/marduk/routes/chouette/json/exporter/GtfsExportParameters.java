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

package no.rutebanken.marduk.routes.chouette.json.exporter;



import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public class GtfsExportParameters {

    public Parameters parameters;

    public GtfsExportParameters(Parameters parameters) {
        this.parameters = parameters;
    }

    public static class Parameters {

        @JsonProperty("gtfs-export")
        public GtfsExport gtfsExport;

        public Parameters(GtfsExport gtfsExport) {
            this.gtfsExport = gtfsExport;
        }

    }

    public static class GtfsExport extends AbstractExportParameters {

        @JsonProperty("object_id_prefix")
        public String objectIdPrefix;

        @JsonProperty("references_type")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public String referencesType = "";

        @JsonProperty("time_zone")
        public String timeZone = "Europe/Oslo";

        @JsonProperty("route_type_id_scheme")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public String routeTypeIdScheme = "extended";

        @JsonProperty("keep_original_id")
        public boolean keepOriginalId = false;

        @JsonProperty("use_tpeg_hvt")
        public boolean useTpegHvt = true;


        public GtfsExport(String name, String objectIdPrefix, String referentialName, String organisationName, String userName, boolean keepOriginalId) {
            this.name = name;
            this.objectIdPrefix = objectIdPrefix;
            this.referentialName = referentialName;
            this.organisationName = organisationName;
            this.userName = userName;
            this.startDate = DateUtils.startDateFor(2L);
            this.endDate = DateUtils.endDateFor(365);
            this.keepOriginalId = keepOriginalId;
        }

    }


}
