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

    private Parameters parameters;

    public Parameters getParameters() {
        return parameters;
    }

    public GtfsImportParameters setParameters(Parameters parameters) {
        this.parameters = parameters;
        return this;
    }

    static class Parameters {

        @JsonProperty("gtfs-import")
        public Gtfs gtfsImport;

    }

    static class Gtfs extends AbstractImportParameters {

        @JsonProperty("object_id_prefix")
        @JsonInclude()
        private String objectIdPrefix;

    	@JsonProperty("split_id_on_dot")
        @JsonInclude()
    	private String splitIdOnDot = "0";

    	@JsonProperty("max_distance_for_commercial")
        private String maxDistanceForCommercial = "0";

        @JsonProperty("ignore_last_word")
        private String ignoreLastWord = "0";

        @JsonProperty("ignore_end_chars")
        private String ignoreEndChars = "0";

        @JsonProperty("max_distance_for_connection_link")
        @JsonInclude()
        private String maxDistanceForConnectionLink = "0";

        @JsonProperty("route_type_id_scheme")
        @JsonInclude()
        private String routeTypeIdScheme = "any";

        @JsonProperty("parse_connection_links")
        @JsonInclude()
        private Boolean parseConnectionLinks = false;

        public String getObjectIdPrefix() {
            return objectIdPrefix;
        }

        public Gtfs setObjectIdPrefix(String objectIdPrefix) {
            this.objectIdPrefix = objectIdPrefix;
            return this;
        }

        public String getSplitIdOnDot() {
            return splitIdOnDot;
        }

        public Gtfs setSplitIdOnDot(String splitIdOnDot) {
            this.splitIdOnDot = splitIdOnDot;
            return this;
        }

        public String getMaxDistanceForCommercial() {
            return maxDistanceForCommercial;
        }

        public Gtfs setMaxDistanceForCommercial(String maxDistanceForCommercial) {
            this.maxDistanceForCommercial = maxDistanceForCommercial;
            return this;
        }

        public String getIgnoreLastWord() {
            return ignoreLastWord;
        }

        public Gtfs setIgnoreLastWord(String ignoreLastWord) {
            this.ignoreLastWord = ignoreLastWord;
            return this;
        }

        public String getIgnoreEndChars() {
            return ignoreEndChars;
        }

        public Gtfs setIgnoreEndChars(String ignoreEndChars) {
            this.ignoreEndChars = ignoreEndChars;
            return this;
        }

        public String getMaxDistanceForConnectionLink() {
            return maxDistanceForConnectionLink;
        }

        public Gtfs setMaxDistanceForConnectionLink(String maxDistanceForConnectionLink) {
            this.maxDistanceForConnectionLink = maxDistanceForConnectionLink;
            return this;
        }

        public String getRouteTypeIdScheme() {
            return routeTypeIdScheme;
        }

        public Gtfs setRouteTypeIdScheme(String routeTypeIdScheme) {
            this.routeTypeIdScheme = routeTypeIdScheme;
            return this;
        }

        public Boolean getParseConnectionLinks() {
            return parseConnectionLinks;
        }

        public Gtfs setParseConnectionLinks(Boolean parseConnectionLinks) {
            this.parseConnectionLinks = parseConnectionLinks;
            return this;
        }
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
