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

package no.rutebanken.marduk.routes.chouette.json.importer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import no.rutebanken.marduk.routes.chouette.json.ChouetteJobParameters;

import java.util.Set;

public class GtfsImportParameters extends ChouetteJobParameters {

    public Parameters parameters;

	static class Parameters {

        @JsonProperty("gtfs-import")
        public Gtfs gtfsImport;

    }

    static class Gtfs extends AbstractImportParameters {

        @JsonProperty("object_id_prefix")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public String objectIdPrefix;

    	@JsonProperty("split_id_on_dot")
        @JsonInclude(JsonInclude.Include.ALWAYS)
    	private String splitIdOnDot = "0";

    	@JsonProperty("max_distance_for_commercial")
        public String maxDistanceForCommercial = "0";

        @JsonProperty("ignore_last_word")
        public String ignoreLastWord = "0";

        @JsonProperty("ignore_end_chars")
        public String ignoreEndChars = "0";

        @JsonProperty("max_distance_for_connection_link")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public String maxDistanceForConnectionLink = "0";

        @JsonProperty("route_type_id_scheme")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public String routeTypeIdScheme = "any";

        @JsonProperty("parse_connection_links")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public Boolean parseConnectionLinks = false;

    }

    public static GtfsImportParameters create(String name, String objectIdPrefix, String referentialName, String organisationName,
                                                     String userName, boolean cleanRepository, boolean enableValidation,
                                                     boolean allowCreateMissingStopPlace, boolean enableStopPlaceIdMapping,
                                                     Set<String> generateMissingRouteSectionsForModes) {
        Gtfs gtfsImport = new Gtfs();
        gtfsImport.name = name;
        gtfsImport.objectIdPrefix = objectIdPrefix;
        gtfsImport.referentialName = referentialName;
        gtfsImport.organisationName = organisationName;
        gtfsImport.userName = userName;
        gtfsImport.cleanRepository = cleanRepository ? "1" : "0";
        gtfsImport.stopAreaRemoteIdMapping = enableStopPlaceIdMapping;
        gtfsImport.generateMissingRouteSectionsForModes = generateMissingRouteSectionsForModes;
        if (allowCreateMissingStopPlace) {
            gtfsImport.stopAreaImportMode = AbstractImportParameters.StopAreaImportMode.CREATE_NEW;
        }
        Parameters parameters = new Parameters();
        parameters.gtfsImport = gtfsImport;
        GtfsImportParameters gtfsImportParameters = new GtfsImportParameters();
        gtfsImportParameters.parameters = parameters;
        gtfsImportParameters.enableValidation = enableValidation;

        return gtfsImportParameters;
    }

}
