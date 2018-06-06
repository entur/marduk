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

public class NetexImportParameters extends ChouetteJobParameters {

    public Parameters parameters;

	static class Parameters {
        @JsonProperty("netexprofile-import")
        public Netex netexImport;
    }

    static class Netex extends AbstractImportParameters {
        @JsonProperty("parse_site_frames")
        @JsonInclude(JsonInclude.Include.ALWAYS)
    	private boolean parseSiteFrames = false;

        @JsonProperty("validate_against_schema")
        @JsonInclude(JsonInclude.Include.ALWAYS)
    	private boolean validateAgainstSchema = true;

        @JsonProperty("validate_against_profile")
        @JsonInclude(JsonInclude.Include.ALWAYS)
    	private boolean validateAgainstProfile = true;

        @JsonProperty("continue_on_line_errors")
        @JsonInclude(JsonInclude.Include.ALWAYS)
    	private boolean continueOnLineErrors = true;

        @JsonProperty("clean_on_error")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private boolean cleanOnErrors = true;

    	@JsonProperty("object_id_prefix")
        @JsonInclude(JsonInclude.Include.ALWAYS)
    	private String objectIdPrefix;

    }

    public static NetexImportParameters create(String name, String referentialName, String organisationName, String userName, boolean cleanRepository,
                                                      boolean enableValidation, boolean allowCreateMissingStopPlace, boolean enableStopPlaceIdMapping,
                                                      String objectIdPrefix, Set<String> generateMissingRouteSectionsForModes) {
        Netex netexImport = new Netex();
        netexImport.name = name;
        netexImport.referentialName = referentialName;
        netexImport.organisationName = organisationName;
        netexImport.userName = userName;
        netexImport.cleanRepository = cleanRepository ? "1":"0";
        netexImport.stopAreaRemoteIdMapping = enableStopPlaceIdMapping;
        netexImport.objectIdPrefix = objectIdPrefix;
        netexImport.generateMissingRouteSectionsForModes = generateMissingRouteSectionsForModes;
        if (allowCreateMissingStopPlace) {
            netexImport.stopAreaImportMode = AbstractImportParameters.StopAreaImportMode.CREATE_NEW;
        }
        Parameters parameters = new Parameters();
        parameters.netexImport = netexImport;
        NetexImportParameters netexImportParameters = new NetexImportParameters();
        netexImportParameters.parameters = parameters;
        netexImportParameters.enableValidation = enableValidation;
        return netexImportParameters;
    }

}
