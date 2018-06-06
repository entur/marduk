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
import no.rutebanken.marduk.routes.chouette.json.AbstractParameters;

import java.util.Set;

public class AbstractImportParameters extends AbstractParameters {

    public enum StopAreaImportMode {
        /**
         * Read only. No changes to stop area repository are persisted during import.
         */
        READ_ONLY,
        /**
         * Create unknown stop areas referenced in import, but never update existing stop areas.
         */
        CREATE_NEW,
        /**
         * Create unknown stop areas and updated existing stop areas referenced in import
         */
        CREATE_OR_UPDATE
    }

    @JsonProperty("clean_repository")
    public String cleanRepository = "0";

    @JsonProperty("no_save")
    public String noSave = "0";
    /**
     * Whether or not stop area ids from import files should be mapped against remote stop area registry (ie NSR).
     */
    @JsonProperty("stop_area_remote_id_mapping")
    public boolean stopAreaRemoteIdMapping = false;

    /**
     * How stop areas in import file should be treated by chouette.
     */
    @JsonProperty("stop_area_import_mode")
    public StopAreaImportMode stopAreaImportMode = StopAreaImportMode.READ_ONLY;

    @JsonProperty("keep_obsolete_lines")
    public boolean keepObsoleteLines = true;

    @JsonProperty("generate_missing_route_sections_for_modes")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public Set<String> generateMissingRouteSectionsForModes;
}
