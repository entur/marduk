package no.rutebanken.marduk.routes.chouette.json.importer;

import com.fasterxml.jackson.annotation.JsonProperty;
import no.rutebanken.marduk.routes.chouette.json.AbstractParameters;

public class AbstractImportParameters extends AbstractParameters {

    @JsonProperty("clean_repository")
    public String cleanRepository = "0";

    @JsonProperty("no_save")
    public String noSave = "0";
    /**
     * Whether or not stop places from import files should be used to update remote stop area repository (NSR).
     */
    @JsonProperty("update_stop_places")
    public boolean updateStopPlaces = false;
    /**
     * Whether or not stop places from import files should be imported to chouette stop area repository.
     */
    @JsonProperty("import_stop_places")
    public boolean importStopPlaces = false;
    
    @JsonProperty("keep_obsolete_lines")
    public boolean keepObsoleteLines = true;
    
    

}
