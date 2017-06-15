package no.rutebanken.marduk.routes.chouette.json.importer;

import com.fasterxml.jackson.annotation.JsonProperty;
import no.rutebanken.marduk.routes.chouette.json.AbstractParameters;

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
     * Whether or not stop places from import files should be used to update remote stop area repository (NSR).
     */
    @JsonProperty("update_external_stop_area_registry")
    public boolean updateExternalStopAreaRegistry = false;

    /**
     * How stop areas in import file should be treated by chouette.
     */
    @JsonProperty("stop_area_import_mode")
    public StopAreaImportMode stopAreaImportMode = StopAreaImportMode.READ_ONLY;

    @JsonProperty("keep_obsolete_lines")
    public boolean keepObsoleteLines = true;


}
